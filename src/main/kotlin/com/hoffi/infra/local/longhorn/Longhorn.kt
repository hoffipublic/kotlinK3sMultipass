package com.hoffi.infra.local.longhorn

import com.hoffi.common.log.LoggerDelegate
import com.hoffi.infra.CONF
import com.hoffi.shell.Shell

object Longhorn {
    private val log by LoggerDelegate()

    fun cluster(nodePrefix: String, format: String, nodeCount: Int): List<String> {
        // check longhorn prerequisites on node:
        // curl -sSfL https://raw.githubusercontent.com/longhorn/longhorn/v1.0.0/scripts/environment_check.sh | bash

        val nn = mutableListOf<String>()
        for (i in 1..nodeCount) {
            nn.add(String.format(format, nodePrefix, i))
        }


        for ( currNode in nn ) {
            Shell.callShell("prepare Longhorn nodes", """
                multipass exec "${currNode}" -- bash -c 'sudo mount --make-rshared /'
            """.trimIndent())
        }

        /* TODO
            If only 1 node is available in your k3s cluster, you will need to enable Replica Node Level Soft Anti-Afinity

            kubectl port-forward -n longhorn-system svc/longhorn-frontend 8002:80
            access frontend: http://localhost:8002
         */

        Shell.callShell("install Longhorn in ${CONF.NAMESPACE_LONGHORN}", """
                if ! helm repo list | grep "^longhorn" ; then
                    helm repo add longhorn https://charts.longhorn.io
                fi
                if [[ ${CONF.NAMESPACE_LONGHORN} != "kube-system" ]] && ! kubectl get namespace "${CONF.NAMESPACE_LONGHORN}" > /dev/null 2>&1 ; then
                    kubectl create namespace "${CONF.NAMESPACE_LONGHORN}"
                fi
                if ! helm ls --namespace "${CONF.NAMESPACE_LONGHORN}" | tail -n +2 | grep "^longhorn" ; then
                    helm install longhorn longhorn/longhorn --namespace "${CONF.NAMESPACE_LONGHORN}"
                fi
            """.trimIndent())

        Shell.callShell("wait for Longhorn ready...", """
                for i in {1..10}; do
                    if kubectl -n "${CONF.NAMESPACE_LONGHORN}" get pod | tail -n +2 | grep -v "Running" ; then
                        kubectl -n "${CONF.NAMESPACE_LONGHORN}" get pod
                        sleep 2
                    else
                        kubectl -n "${CONF.NAMESPACE_LONGHORN}" get pod
                        kubectl -n "${CONF.NAMESPACE_LONGHORN}" get svc
                        break
                    fi
                done
            """.trimIndent())

        Shell.callShell("configure Longhorn", """
                kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}'
                Sleep 1
                kubectl get storageclass
            """.trimIndent())

        val applyYaml = """
            apiVersion: networking.k8s.io/v1
            kind: Ingress
            metadata:
              name: longhorn-ingress
              namespace: ${CONF.NAMESPACE_LONGHORN}
              annotations:
                kubernetes.io/ingress.class: traefik
            spec:
              rules:
              - host: longhorn.${CONF.MYCLUSTER_DOMAIN}
                http:
                  paths:
                  - path: /
                    pathType: Prefix
                    backend:
                      service:
                        name: longhorn-frontend
                        port:
                          number: 80
        """.trimIndent().trim()
        //kubectl -n "${CONF.NAMESPACE_LONGHORN}" apply -f echo <(printf "${applyYaml}")
        Shell.callShell("expose longhorn-frontend", """
            echo '${applyYaml}' | kubectl -n "${CONF.NAMESPACE_LONGHORN}" apply -f -
        """.trimIndent().trim())

        return nn
    }
}
