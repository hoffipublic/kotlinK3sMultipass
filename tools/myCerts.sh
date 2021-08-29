#!/usr/bin/env bash

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd $SCRIPTDIR/..

BASEDIR=tmp/local/certs
HOST_DOMAIN=hoffilocal.com
MYCLUSTER_DOMAIN=iac.poi
rootCAissuer=rootcahoffi

finish() {
    set +x
}
trap finish EXIT

## set -E # better ERR trap handling
## set -e # exit the script on first error (command not returning $?=0)
## set -u # errors if an variable is referenced before being set
## set -o pipefail # fail fast if an erroneous command pipes to downstream commands
#export DEFAULT_SHELLOPTS="-Eeuo pipefail" # https://vaneyckt.io/posts/safer_bash_scripts_with_set_euxo_pipefail/
#set ${DEFAULT_SHELLOPTS}

set -Eeuo pipefail
set -x
set +e

# rootca
tools/createCert.sh --cmd=rootca --basedir="$BASEDIR" \
  --basename=${rootCAissuer} --email='hoffi.mail@gmail.com' --country=DE --state=Bavaria --location=Munich --organization=priv --organizationalUnit=hoffi \
  ${rootCAissuer} ${HOST_DOMAIN}


# harbor image registry
tools/createCert.sh --cmd=cert   --basedir="$BASEDIR" --group=harbor \
  --basename=registry        --email='hoffi.mail@gmail.com' --country=DE --state=Bavaria --location=Munich --organization=priv --organizationalUnit=hoffi \
  registry.${HOST_DOMAIN} localhost 127.0.0.1
# harbor docker
tools/createCert.sh --cmd=cert   --basedir="$BASEDIR" --group=harbor \
  --basename=docker         --email='hoffi.mail@gmail.com' --country=DE --state=Bavaria --location=Munich --organization=priv --organizationalUnit=hoffi \
  registry.${HOST_DOMAIN} localhost 127.0.0.1


# test stuff
tools/createCert.sh --cmd=cert   --basedir="$BASEDIR" --group=test \
  --basename=client          --email='hoffi.mail@gmail.com' --country=DE --state=Bavaria --location=Munich --organization=priv --organizationalUnit=hoffi \
  testclient

# curl --cacert certs/rootca_rootcahoffi.ca \
#      --key    certs/certs/client.key \
#      --cert    certs/certs/client.cert \
#   https://test.hoffilocal.com:8081
#
# curl --cacert certs/rootca_rootcahoffi.ca \
#      --key    certs/certs/client.key \
#      --cert    certs/certs/client.cert \
#      -L \
#   http://test.hoffilocal.com:8080
