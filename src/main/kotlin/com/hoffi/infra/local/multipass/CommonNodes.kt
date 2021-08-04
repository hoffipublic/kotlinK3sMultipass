package com.hoffi.infra.local.multipass

import com.github.ajalt.clikt.output.TermUi.echo
import com.hoffi.common.helpers.file.FileUtils.END_SENTINEL
import com.hoffi.common.helpers.file.FileUtils.START_SENTINEL
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF
import com.hoffi.shell.Shell
import com.hoffi.yaml.YAML
import java.io.File

data class vmState(
    val name: String,
    val IP: String,
    val currentState: String,
    val formerState: String,
)

object CommonNodes {
    private val log by LoggerDelegate()
    private val singleNode = "--cpus 4 --mem 6G --disk 15G"
    private val multiNode = "--cpus 2 --mem 2G --disk 5G"

    fun cluster(nodePrefix: String, format: String, nodeCount: Int): List<vmState> {
        // generate cloud-config.yml
        val yttOutDir = File("${CONF.DIR.GENDIR}/common")
        yttOutDir.mkdirs()
        val yttOutFilename = "${yttOutDir}/cloud-config.yml"
        val yttOutFile = File(yttOutFilename)
        val yttOut = YAML.callYtt(name = yttOutFilename, listOf("${CONF.DIR.CONFDIR}/common/cloud-config.yml"))
        yttOutFile.writeText(yttOut)

        // create or start multipass VMs
        val vmStates = mutableListOf<vmState>()
        var multipassListJson = Shell.callShell("multipass list", "multipass ls --format json | jq \"[.list[] | select(.name | startswith(\\\"${nodePrefix}\\\"))]\"")
        for (i in 1..nodeCount) {
            val currNode = String.format(format, nodePrefix, i)
            val currNodeSTATE = Shell.callShell("multipass get ${currNode} state", "echo '$multipassListJson' | jq -r \".[] | select(.name == \\\"${currNode}\\\").state\"", noLogging = true)
            if (currNodeSTATE == "Stopped") {
                log.info("starting ${if (nodeCount <= 1) "single" else "$i (of $nodeCount)"} ${currNode} ${CONF.MULTIPASSBASEOS} VM")
                vmStates.add(vmState(currNode, "unknown", currNodeSTATE, currNodeSTATE))
                Shell.callShell("", "multipass start \"$currNode\"")
            } else if (currNodeSTATE == "Running") {
                log.info("${if (nodeCount <=1) "single" else "$i (of $nodeCount)"} VM ${currNode} already up and running")
                vmStates.add(vmState(currNode, "unknown", currNodeSTATE, currNodeSTATE))
            } else {
                log.info("creating ${if (nodeCount <= 1) "single" else "$i (of $nodeCount)"} ${currNode} VM from ${CONF.MULTIPASSBASEOS}")
                vmStates.add(vmState(currNode, "unknown", "fresh", "fresh"))
                Shell.callShell("", "multipass launch --name \"${currNode}\" ${if (nodeCount <=1) singleNode else multiNode} --cloud-init \"${yttOutFile}\" \"${CONF.MULTIPASSBASEOS}\"")
            }
        }

        if (vmStates.filter { it.formerState == "fresh" }.isNotEmpty()) {
            log.info("waiting a bit for VMs to come up...")
            Thread.sleep(2_000)
        }

        // get all node names and their IPs
        multipassListJson = Shell.callShell("multipass list", "multipass ls --format json | jq \"[.list[] | select(.name | startswith(\\\"${nodePrefix}\\\"))]\"")
        val vmStatesCopy = ArrayList<vmState>(vmStates)
        vmStates.clear()
        for (currVmState in vmStatesCopy) {
            val currNodeSTATE = Shell.callShell("multipass get ${currVmState.name} state", "echo '$multipassListJson' | jq -r \".[] | select(.name == \\\"${currVmState.name}\\\").state\"", noLogging = true)
            val currNodeIP = Shell.callShell("multipass get ${currVmState.name} state", "echo '$multipassListJson' | jq -r \".[] | select(.name == \\\"${currVmState.name}\\\").ipv4[0]\"", noLogging = true)
            vmStates.add(currVmState.copy(IP = currNodeIP, currentState = currNodeSTATE))
        }

        return vmStates
    }

    fun freshInstall(vmStates: List<vmState>) {
        val linuxPackages = YAML.linuxPackages()
        for (vmState in vmStates) {
            log.info("fresh install ${vmState}:")
            val aptPackages = linuxPackages["packages"]?.map { it.name }?.joinToString(" ") ?: "none"
            val snapPackages = linuxPackages["snapPackages"]?.map { it.name }?.joinToString(" ") ?: "none"
            val etcHosts = vmStates.map { "echo -e \"${it.IP} ${it.name}\" >> /etc/hosts" }.joinToString("\n")
            val checkAptPackages = linuxPackages["packages"]?.map { it.versionCmd }?.toMutableList() ?: mutableListOf()
            val checkSnapPackages = linuxPackages["snapPackages"]?.map { it.versionCmd } ?: mutableListOf()
            checkAptPackages.addAll(checkSnapPackages)
            val checkPackages = checkAptPackages.joinToString("\n")
            val sudoNoPassword = "\"s/^(%sudo[[:space:]]+).*\$/\\1ALL=(ALL:ALL) NOPASSWD: ALL/\""
            val remoteCmd = """
                    multipass exec ${vmState.name} -- bash +e -x -c '
                        sudo sed -E -i ${sudoNoPassword} /etc/sudoers
                        sudo apt update
                        sudo apt install -y ${aptPackages}
                        sudo snap install ${snapPackages}
                        if fzf --version >/dev/null 2>&1 ; then echo "source /usr/share/doc/fzf/examples/key-bindings.bash" >> /home/ubuntu/.bashrc ; fi
                        sudo chown ubuntu:ubuntu /etc/hosts
                        echo -e "\n${START_SENTINEL}" >> /etc/hosts
                        ${etcHosts}
                        echo -e "${END_SENTINEL}" >> /etc/hosts
                        echo -e "${File("${CONF.DIR.HOME}/.ssh/id_rsa.pub").readText()}" >> /home/ubuntu/.ssh/authorized_keys
                        # TODO add self-signed cert to /usr/ TargetEnvs.local /share/ca-certificates/ or /etc/ssl/certs/
                        #
                        echo
                        echo -e "informational for ${vmState.name}:"
                        echo
                        echo "uname -a"
                        uname -a
                        echo
                        echo "cat /etc/systemd/resloved.conf"
                        cat /etc/systemd/resolved.conf
                        echo
                        echo "tail /etc/hosts"
                        tail /etc/hosts
                        echo
                        echo "tail ~/.ssh/authorized_keys"
                        tail ~/.ssh/authorized_keys
                        echo
                        echo "checking installed packages:" 
                        ${checkPackages}
                    '
            """.trimIndent()
            echo("THE REMOTE CMD TO EXECUTE:")
            echo("==========================")
            echo(remoteCmd)
            echo("==========================")
            Shell.callShell("fresh install ${vmState.name}", remoteCmd)
        }
    }
}
