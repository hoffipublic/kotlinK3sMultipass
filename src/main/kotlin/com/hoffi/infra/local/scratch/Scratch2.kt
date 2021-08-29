package com.hoffi.infra.local.scratch

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF
import com.hoffi.infra.local.multipass.vmState
import com.hoffi.shell.Shell
import net.mamoe.yamlkt.Yaml
import java.io.File


class Scratch2: CliktCommand() {
    private val log by LoggerDelegate()
    val yaml = Yaml()

    override fun run() {
        log.info("should have a greeting.")

        val vmState = vmState("harbor1", "192.168.64.17", "fresh", "fresh")

        var remoteCmd = """
                    multipass mount ~/devTools/harbor/data ${vmState.name}:/data
                    multipass exec ${vmState.name} -- bash +e -x -c '
                        function mycall() { echo "${'$'} ${'$'}@" ; eval "${'$'}@" ; }
                        mycall mkdir harbor 2>/dev/null
                        mycall cd harbor
                        mycall mkdir certs 2>/dev/null
                        # docker wants cert postfix to be .crt not .cert or anything else
                        echo -e "${File("tmp/${CONF.targetEnv}/certs/harbor/certs/harbor_registry.cert").readText()}" > certs/harbor_registry.crt 
                        echo -e "${File("tmp/${CONF.targetEnv}/certs/harbor/certs/harbor_registry.key").readText()}" > certs/harbor_registry.key 
                        mycall cd ..
                        mycall pwd
                        
                        latest=${'$'}(curl -sL https://api.github.com/repos/goharbor/harbor/releases/latest |  jq -r ".tag_name")
                        mycall wget https://github.com/goharbor/harbor/releases/download/${'$'}latest/harbor-online-installer-${'$'}latest.tgz
                        mycall tar xf harbor-online-installer-${'$'}latest.tgz
                        mycall cd harbor
                        yq eval -o=j harbor.yml.tmpl | jq "
                            .hostname = \"registry.hoffilocal.com\" |
                            .http.port = 8880 |
                            .https.port = 8843 |
                            .https.certificate = \"${'$'}PWD/certs/harbor_registry.crt\" |
                            .https.private_key = \"${'$'}PWD/certs/harbor_registry.key\" |
                            .data_volume = \"/data\" |
                            .log.local.rotate_size = \"15M\"
                        " | yq eval --prettyPrint > harbor.yml
                    '
                """.trimIndent()
        Shell.callShell("harbor install on ${vmState.name}", remoteCmd)


        log.info("done.")
    }
}
