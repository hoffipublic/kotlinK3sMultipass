package com.hoffi.infra.local.scratch

import com.github.ajalt.clikt.core.CliktCommand
import com.hoffi.common.log.LoggerDelegate
import com.hoffi.shell.Shell
import net.mamoe.yamlkt.Yaml


class Scratch2: CliktCommand() {
    private val log by LoggerDelegate()
    val yaml = Yaml()

    override fun run() {
        log.info("should have a greeting.")

        //val yamlConf = Yaml.decodeMapFromString(File(CONF.DIR.CONFDIR, "common/linuxPackages.yml").readText())

//        val applyYaml = """
//            apiVersion: networking.k8s.io/v1
//            kind: Ingress
//            metadata:
//              name: longhorn-ingress
//              namespace: ${CONF.NAMESPACE_LONGHORN}
//              annotations:
//                kubernetes.io/ingress.class: traefik
//            spec:
//              rules:
//              - host: longhorn.${CONF.MYCLUSTER_DOMAIN}
//                http:
//                  paths:
//                  - path: /
//                    pathType: Prefix
//                    backend:
//                      service:
//                        name: longhorn-frontend
//                        port:
//                          number: 80
//        """.trimIndent().trim()
//        // kubectl -n longhorn-system port-forward services/longhorn-frontend 8080:http
//        //kubectl -n "${CONF.NAMESPACE_LONGHORN}" apply -f echo <(printf "${applyYaml}")
//        Shell.callShell("expose longhorn-frontend", """
//            echo '${applyYaml}' | kubectl -n "${CONF.NAMESPACE_LONGHORN}" apply -f -
//        """.trimIndent().trim())

        Shell.callShell("retrying 10 times to get k3s traefik ingress IP", """
            for i in {1..10}; do
                if kubectl get service -l app.kubernetes.io/instance=traefik -n kube-system -o json 2>/dev/null | jq -r '[.items[0].status.loadBalancer.ingress[].ip] | @sh' ; then
                    break
                fi
                sleep 3
            done
        """.trimIndent())


        log.info("done.")
    }
}
