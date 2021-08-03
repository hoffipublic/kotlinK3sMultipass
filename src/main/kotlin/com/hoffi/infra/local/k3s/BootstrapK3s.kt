package com.hoffi.infra.local.k3s

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.helpers.file.FileUtils
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF
import com.hoffi.infra.local.multipass.vmState
import com.hoffi.shell.Shell
import koodies.exec.CommandLine
import java.io.File
import java.nio.file.Path

class BootstrapK3s: CliktCommand(printHelpOnEmptyArgs = true, help = """
    bootstrap K8s Rancher K3s flavor on an existing VM cluster    
""".trimIndent())
{
    private val log by LoggerDelegate()

    override fun run() {

        @Suppress("UNCHECKED_CAST")
        val k3sVmStates = CONF.tmpMap[CONF.K3SNODENAMEPREFIX] as List<vmState>

        // skip all of this if all VMs already had been up
        if (k3sVmStates.filterNot { it.formerState == "Running" }.isEmpty()) {
            log.info("skipping BootStrapK3s.")
            return
        } else {
            log.info("running BootStrapK3s.")
        }

        val k3sYamlFile = Path.of("${CONF.DIR.TMPDIR}/k3s.yaml")
        val k3sRootCaFile = Path.of("${CONF.DIR.TMPDIR}/${CONF.K3SNODENAMEPREFIX}_root.ca")
        var k3sMasterToken = ""


        Shell.callShell("start K3S master node", """
            multipass exec "${k3sVmStates[0].name}" -- /bin/bash -c "curl -sfL -C - https://get.k3s.io | sh -"
        """.trimIndent())

        Shell.callShell("get /etc/rancher/k3s/k3s.yaml", """
            multipass exec "${k3sVmStates[0].name}" -- bash -c 'sudo cat /etc/rancher/k3s/k3s.yaml' > "${k3sYamlFile}"
        """.trimIndent())
        FileUtils.replaceInline(k3sYamlFile, keepOriginal = true) { line ->
            line.replace("127.0.0.1", k3sVmStates[0].name)
        }

        if (CONF.HOST_KUBECONFIG_FILE.toString().isBlank()) {
            echo("")
            echo("sudo rights needed, please execute the following command(s) manually...")
            echo("")
            echo("sudo bash -c '")
            echo("export KUBECONFIG=\"${k3sYamlFile}\"")
            echo("'")
            echo("")
            echo("if done that (in another shell!), press Enter key")
            //System.`in`.readAllBytes() // throw away already typed stuff
            readLine()
        } else {
            try { File("${CONF.DIR.HOME}/.kube/config").copyTo(File("${CONF.DIR.HOME}/.kube/config.orig"), overwrite = true) } catch(e: Exception) {}
            k3sYamlFile.toFile().copyTo(File("${CONF.DIR.HOME}/.kube/config"), overwrite = true)
        }

        Shell.callShell("get k3s root.ca", """
            kubectl config view --raw --minify --flatten -o jsonpath='{.clusters[].cluster.certificate-authority-data}' | base64 -d > "${k3sRootCaFile}"
        """.trimIndent())

        k3sMasterToken = Shell.callShell("Get the K3S_TOKEN from the master node", """
            multipass exec "${k3sVmStates[0].name}" -- /bin/bash -c "sudo cat /var/lib/rancher/k3s/server/node-token"
        """.trimIndent())

        if ( k3sMasterToken.isBlank() || k3sMasterToken.matches(Regex("^.* .*$"))) {
            throw Exception("couldn't get K3S_TOKEN:  K3S_TOKEN=${k3sMasterToken} (check DNS lookup (e.g. dnsmasq and/or /etc/rosolv.conf /etc/systemd/resolved.conf is/are configured correctly)")
        }

        if (CONF.K3SNODECOUNT <= 1) {
            // TODO kubewaitPodRunning
            // kubewaitPodRunning -n "kube-system" -l app.kubernetes.io/name=traefik 'traefik-.*' 1 4 25 1 # initial:5 between:5 times:20 after:1
        }

        var i = 1
        while ( i < CONF.K3SNODECOUNT) {
            Shell.callShell("spinning up K8s slave ${i} ${k3sVmStates[i].name} at ${k3sVmStates[i].IP}", """
                multipass exec "${k3sVmStates[i].name}" -- /bin/bash -c "curl -sfL -C - https://get.k3s.io | K3S_TOKEN=${k3sMasterToken} K3S_URL=https://${k3sVmStates[0].IP}:6443 sh -"
            """.trimIndent())
            i++
        }

        if (CONF.K3SNODECOUNT > 1) {
            Thread.sleep(3000L)
        }

        log.info("post cluster installation stuff...")
        CommandLine("kubectl", "taint", "node", k3sVmStates[0].name, "--overwrite", "node-role.kubernetes.io/master=effect:NoSchedule")
            .exec()
        i = 1
        while ( i < CONF.K3SNODECOUNT) {
            while(true) {
                val state = Shell.callShell("checking if node ${k3sVmStates[i].name} is ready", """
                    kubectl get node ${k3sVmStates[i].name} | tail -1 | awk '{print ${'$'}2}'
                """.trimIndent()).trim()
                if (state == "Ready") break
                Thread.sleep(2_000L)
            }
            CommandLine("kubectl", "label", "node", k3sVmStates[i].name, "--overwrite", "--overwrite node-role.kubernetes.io/node=")
                .exec()
            i++
        }

        CommandLine("kubectl", "get", "nodes", "-o", "wide")
            .exec()

        // TODO kubewaitPodRunning -n "kube-system" -l app.kubernetes.io/name=traefik 'traefik-.*' 5 5 20 1 # initial:5 between:5 times:20 after:1

        for (i in 1..5) {
            try {
                val ingressIP = Shell.callShell("get k3s traefik ingress IP", """
                    kubectl get service -l app.kubernetes.io/instance=traefik -n kube-system -o json | jq -r '[.items[0].status.loadBalancer.ingress[].ip] | @sh'
                """.trimIndent())
                break
            } catch (e: Exception) {
                log.info("trying again after 3 sec")
                Thread.sleep(3_000L)
            }
        }

        log.info("ran BootStrapK3s.")
    }
}
