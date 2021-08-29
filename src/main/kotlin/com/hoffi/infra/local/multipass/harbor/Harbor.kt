package com.hoffi.infra.local.multipass.harbor

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF
import com.hoffi.infra.local.multipass.CommonNodes
import com.hoffi.shell.Shell
import com.hoffi.yaml.YAML
import java.io.File

class Harbor: CliktCommand(printHelpOnEmptyArgs = false, help = """
    setup secure harbor container image registry
""".trimIndent())
{
    private val log by LoggerDelegate()

    override fun run() {
        log.info("running Harbor.")

        val yttOutDir = File("${CONF.DIR.GENDIR}/common")
        yttOutDir.mkdirs()
        val yttOutFilename = "${yttOutDir}/cloud-config.yml"
        val yttOutFile = File(yttOutFilename)
        val yttOut = YAML.callYtt(name = yttOutFilename, listOf("${CONF.DIR.CONFDIR}/common/cloud-config.yml"))
        yttOutFile.writeText(yttOut)

        val vmStates = CommonNodes.cluster("harbor", "%s%d", 1)

        //if (true || vmStates.any { it.formerState == "fresh" }) {
            var info = "installing docker-ce and docker-compose"
            log.info("now $info ...")
        //    vmStates.filter { true || it.formerState == "fresh" }.forEach { vmName ->
                val vmName = vmStates[0].name
                var remoteCmd = """
                    multipass exec ${vmName} -- bash +e -x -c '
                        function mycall() { echo "${'$'} ${'$'}@" ; eval "${'$'}@" ; }
                        if docker --version >/dev/null ; then echo "docker already installed" ; exit 0 ; fi
                        mycall sudo apt install -y apt-transport-https
                        mycall curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
                        mycall sudo add-apt-repository \"deb [arch=amd64] https://download.docker.com/linux/ubuntu focal stable\"
                        mycall sudo apt update
                        mycall apt-cache policy docker-ce
                        mycall sudo apt install -y docker-ce containerd.io
                        mycall sleep 2
                        #mycall sudo systemctl --no-pager status -l docker
                        mycall sudo groupadd docker
                        mycall sudo usermod -aG docker ${'$'}USER
                        #mycall sudo newgrp docker
                        mycall sudo docker run hello-world

                        latest=${'$'}(curl -sL https://api.github.com/repos/docker/compose/releases/latest |  jq -r ".tag_name")
                        mycall sudo wget --no-verbose -O /usr/bin/docker-compose https://github.com/docker/compose/releases/download/${'$'}latest/docker-compose-${'$'}(uname -s)-${'$'}(uname -m)
                        sudo chmod 755 /usr/bin/docker-compose
                        docker-compose --version
                    '
                """.trimIndent()


                echo("THE REMOTE CMD TO EXECUTE:")
                echo("==========================")
                echo(remoteCmd)
                echo("==========================")
                Shell.callShell("$info ${vmName}", remoteCmd)

            info = "exposing docker daemon with TLS"
            log.info("now $info ...")
                val docker_certsDir = "/etc/docker/certs"
                val daemonJson = """
                {
                    \"hosts\": [\"unix:///var/run/docker.sock\", \"tcp://0.0.0.0:2376\"],
                    \"tls\": true,
                    \"tlscacert\": \"${docker_certsDir}/rootca_rootcahoffi.ca\",
                    \"tlscert\":   \"${docker_certsDir}/harbor_docker.cert\",
                    \"tlskey\":    \"${docker_certsDir}/harbor_docker.key\"
                }""".trimIndent()
                val rootCAbasename = CONF.HOST_DOMAIN_CA.replace(Regex("^.*/(.*).ca"), "$1")
                val harbor_docker_cert = "tmp/${CONF.targetEnv}/certs/harbor/certs/harbor_docker.cert"
                val harbor_docker_key = "tmp/${CONF.targetEnv}/certs/harbor/certs/harbor_docker.key"
                remoteCmd = """
                    multipass exec ${vmName} -- bash +e -x -c '
                        function mycall() { echo "${'$'} ${'$'}@" ; eval "${'$'}@" ; }
                        mycall sudo mkdir -p "${docker_certsDir}"
                        sudo echo -e "${File(CONF.HOST_DOMAIN_CA).readText()}" | sudo tee "${docker_certsDir}/${rootCAbasename}.ca"
                        sudo echo -e "${File(harbor_docker_cert).readText()}" | sudo tee "${docker_certsDir}/harbor_docker.cert"
                        sudo echo -e "${File(harbor_docker_key).readText()}" | sudo tee "${docker_certsDir}/harbor_docker.key"
                        sudo chmod 600 "${docker_certsDir}/${rootCAbasename}.ca"
                        sudo chmod 600 "${docker_certsDir}/harbor_docker.cert"
                        sudo chmod 600 "${docker_certsDir}/harbor_docker.key"
                        sudo rm /etc/docker/daemon.json 2>/dev/null
                        sudo echo -e "${daemonJson}" | sudo tee /etc/docker/daemon.json
                        # modify docker systemd service to remove all -H parameters like -H fd:// -H tcp://...
                        sudo awk -i inplace "/^ExecStart=/ { gsub(/-H [^ ]+ /, \"\") } 1" /lib/systemd/system/docker.service
                        sudo systemctl daemon-reload
                        # sudo systemctl restart docker
                    '
                """.trimIndent()
                echo("THE REMOTE CMD TO EXECUTE:")
                echo("==========================")
                echo(remoteCmd)
                echo("==========================")
                Shell.callShell("$info on ${vmName}", remoteCmd)

                log.info("to access harbor's docker from the host (not inside VM):")
                log.info("  ~/.docker/ca.pem")
                log.info("  docker --tls --tlscacert=${CONF.HOST_DOMAIN_CA} --tlscert=path/to/clientCertSignedByRootCA --tlskey=path/to/clientCertKey -H=/${vmName}.${CONF.HOST_DOMAIN}:2376 ps")
                log.info("  e.g.:")
                log.info("  docker --tls --tlscacert=${CONF.HOST_DOMAIN_CA} --tlscert=${harbor_docker_cert} --tlskey=${harbor_docker_key} -H=/${vmName}.${CONF.HOST_DOMAIN}:2376 ps")

                info = "telling docker how to access harbor securely"
                log.info("now $info ...")
                val registry_http_port = 8880
                val registry_https_port = 8843
                val harbor_cert = "tmp/${CONF.targetEnv}/certs/harbor/certs/harbor_registry.cert"
                val harbor_key = "tmp/${CONF.targetEnv}/certs/harbor/certs/harbor_registry.key"
                val docker_registryCertsDir = "/etc/docker/certs.d/registry.${CONF.HOST_DOMAIN}:${registry_https_port}"
                // The Docker daemon interprets .crt files as CA certificates and .cert files as identity (client/server) certificates
                val harbor_caName = "${rootCAbasename}.crt"
                remoteCmd = """
                    multipass exec ${vmName} -- bash +e -x -c '
                        function mycall() { echo "${'$'} ${'$'}@" ; eval "${'$'}@" ; }
                        # registry runs on THIS vm, so it is at localhost (make sure localhost is a SAN (subject alternate name) of that certificate!
                        # this should be a client certificate signed by rootCA, but as we are on the same host, we simply take harbor its server cert 
                        # also the Docker daemon interprets .crt files as CA certificates and .cert files as identity (client/server) certificates
                        mycall sudo mkdir -p "${docker_registryCertsDir}"
                        sudo chmod 700 "/etc/docker/certs.d"
                        sudo chmod 700 "${docker_registryCertsDir}"
                        
                        sudo echo -e "${File(CONF.HOST_DOMAIN_CA).readText()}" | sudo tee "${docker_registryCertsDir}/${harbor_caName}"
                        sudo echo -e "${File(harbor_cert).readText()}" | sudo tee "${docker_registryCertsDir}/harbor_registry.cert"
                        sudo echo -e "${File(harbor_key).readText()}" | sudo tee "${docker_registryCertsDir}/harbor_registry.key"
                        sudo chmod 600 "${docker_registryCertsDir}/${harbor_caName}"
                        sudo chmod 600 "${docker_registryCertsDir}/harbor_registry.cert"
                        sudo chmod 600 "${docker_registryCertsDir}/harbor_registry.key"

                    '
                """.trimIndent()
                echo("THE REMOTE CMD TO EXECUTE:")
                echo("==========================")
                echo(remoteCmd)
                echo("==========================")
                Shell.callShell("$info on ${vmName}", remoteCmd)


                log.info("now installing Harbor registry...")
                remoteCmd = """
                    multipass mount ~/devTools/harbor/data ${vmName}:/data
                    multipass exec ${vmName} -- bash +e -x -c '
                        function mycall() { echo "${'$'} ${'$'}@" ; eval "${'$'}@" ; }
                        mycall mkdir harbor 2>/dev/null
                        mycall cd harbor
                        mycall mkdir certs 2>/dev/null
                        echo -e "${File(harbor_cert).readText()}" > certs/harbor_registry.cert 
                        echo -e "${File(harbor_key).readText()}" > certs/harbor_registry.key 
                        chmod 600 certs/harbor_registry.cert
                        chmod 600 certs/harbor_registry.key
                        mycall cd ..
                        mycall pwd
                        

                        mycall sudo systemctl restart docker
                        
                        
                        latest=${'$'}(curl -sL https://api.github.com/repos/goharbor/harbor/releases/latest |  jq -r ".tag_name")
                        mycall wget https://github.com/goharbor/harbor/releases/download/${'$'}latest/harbor-online-installer-${'$'}latest.tgz
                        mycall tar xf harbor-online-installer-${'$'}latest.tgz
                        mycall cd harbor
                        yq eval -o=j harbor.yml.tmpl | jq "
                            .hostname = \"registry.hoffilocal.com\" |
                            .http.port = ${registry_http_port} |
                            .https.port = ${registry_https_port} |
                            .https.certificate = \"${'$'}PWD/certs/harbor_registry.cert\" |
                            .https.private_key = \"${'$'}PWD/certs/harbor_registry.key\" |
                            .data_volume = \"/data\" |
                            .TargetEnvs.local.rotate_size = \"15M\" |
                            .harbor_admin_password = \"Admin123\"
                        " | yq eval --prettyPrint > harbor.yml
                        
                        mycall sudo ./install.sh
                        sudo docker-compose ps
                    '
                """.trimIndent()
                echo("THE REMOTE CMD TO EXECUTE:")
                echo("==========================")
                echo(remoteCmd)
                echo("==========================")
                Shell.callShell("$info on ${vmName}", remoteCmd)
        //    }
        //}

        log.warn("... not waiting for harbor registry to come up ... !!!")

        log.info("ran Harbor.")
    }
}

