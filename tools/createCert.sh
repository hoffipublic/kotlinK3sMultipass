#!/usr/bin/env bash

function usage() {
    local SCRIPTNAME=${BASH_SOURCE[1]##*/}
    cat <<-EOT
Usage: ${SCRIPTNAME} --info [--theOption=value] [path/to/cert]...
       or
       ${SCRIPTNAME} --cmd=(ssh|rsa|sign) [--theOption=value]
       or
       ${SCRIPTNAME} --cmd=(selfsignedcert|cert|csr) [--theOption=value] theDomainAkaCN[:port] [domainAlias]... [domainIP]...
       or
       ${SCRIPTNAME} --cmd=rootca [--theOption=value] issuerName theDomainAkaCN [domainAlias]... [domainIP]...
       beware!:
       domainAliases are not allowed to have a :port
  --info -i            just output info on the given certificate(s) given as args (not as the opt value)
  --cmd=(ssh|rsa)      create a rsa ssh private/public key pair (.eg. for ssh'ing to some machine)
  --cmd=selfsignedcert create an independent self-signed certificate
  --cmd=cert           create a cert signed by given rootca (by default rootca is expected in basedir/rootca_*.{ca,key})
  --cmd=rootca         create a self signed root ca (identifying a root certificate authority) (filenames will be prefixed with 'rootca_')
  --cmd=csr            create a csr (certificate signing request) (by default rootca is expected in basedir/rootca_*.{ca,key})

  --group=groupName    (default: '') an identifier used for a) subdirs in basedir and b) prefix (+ '_') in names for generated files (except for rootca)
  --basename=baseName  (default: 'id_rsa' for ssh|rsa OR 'cert' for certs OR 'root' for rootca) basename of generated files
  --basedir=path/to    (default: 'certs') base target directory, expected/created structure will be:
                       basedir/
                       |-- rootca_groupname_basename.ca
                       |-- rootca_groupname_basename.key
                       \`-- groupname/
                           |-- certs/
                           |  |-- groupname_basename.key
                           |  \`-- groupname_basename.cert
                           \`-- ssh/
                               |-- groupname_basename
                               \`-- groupname_basename.pub

  --cacertfile=path/file.ca ignore rootca_*.ca  file in --basedir use this one instead
  --cakeyfile=path/file.key ignore rootca_*.key file in --basedir use this one instead
  --destdir=path/to         ignore --groupname and --basedir and write (flat!) to the given dir (needed rootca_*.{ca,key} have to be also there or be given)
  --destfilesbasename=name  ignore --groupname and --basename and name outputfile like this (without postfixes like .cert, .pem or .key)

  --keybits=2048            (default: 2048) bit-length of generated private keys
  --email=some@email        (default: \`git config --get user.email\`) email to use as comment inside certs or ssh/rsa pub key
  --country="DE"            (default: extracted by call to http://ipwhois.app/json/)
  --state="Bavaria"         (default: extracted by call to http://ipwhois.app/json/)
  --location="Munich"       (default: extracted by call to http://ipwhois.app/json/)
  --organization="Comp"     (default: Company)
  --organizationalUnit="IT" (default: IT department)
  --days=730                (default: two years) validity of certs/rootca


  --help -h     print this help
  --silent -s   less output
  --force       force recreation/overwriting of already existing files
EOT
}

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

##############################################################################
# main
##############################################################################
function main() {
    if $OPT_INFO ; then
        printCertInfo "${ARGS[@]}"
        return
    fi
    case "${OPT_CMD}" in
        'ssh'|'rsa')
            sshFiles
            ;;
        'csr')
            csrFiles
            ;;
        'selfsignedcert'|'cert'|'sign')
            if csrFiles ; then
                signCsr
            fi
            ;;
        'rootca')
            rootCAfiles
            ;;
        *)
            >&2 echo "unknown cmd ${OPT_CMD}"
            ;;
    esac
}

function sshFiles() {
    local errormessage="$(\
        checkAndMaybeMoveExistingFiles "$(destDir ssh)" \
            "$(destDir ssh)/$(fileName sshkey)" \
            "$(destDir ssh)/$(fileName sshpub)" \
    )"
    if [[ -n "$errormessage" ]]; then # OPT_FORCE=false && files already exist
        >&2 echo "ssh/rsa files (some) already exist! skipping operation..."
        return 2
    #else # FORCE=true ==> moving files to a tmpdir within $(destDir ssh)
    fi

    SILENT=""
    if $OPT_SILENT ; then SILENT=" -q" ; fi
    local cmd="ssh-keygen${SILENT} -t rsa -m PEM -b ${OPT_KEYBITS} -N '' -f \"$(destDir ssh)/$(fileName ssh)\"  -C \"${OPT_EMAIL}\" <<< y"
    if ! $OPT_SILENT ; then echo "$cmd" ; fi
    eval "$cmd"
    if ! $OPT_SILENT ; then
        ssh-keygen -E md5 -lf "$(destDir ssh)/$(fileName sshkey)" # print MD5 hash of generated private key
    fi
}

function printCertInfo() {
    if [[ ${#@} -eq 0 ]]; then
        >&2 echo "no args given"
        exit 8
    fi
    local allFiles="\n${0##*/} created files:\n=============="
    for arg in "$@"; do
        if [[ ! -f "$arg" ]]; then continue ; fi
        echo "${arg//?/=}=" # underline
        echo "$arg:"
        allFiles="${allFiles}\n${arg}"
        local cmd
        cmd="openssl dgst -md5"
        if ! $OPT_SILENT ; then echo -n "$cmd ${arg##*/} : " ; fi
        eval "$cmd ${arg}"
        if ssh-keygen -E md5 -lf "${arg}" >/dev/null 2>&1 ; then
            cmd="ssh-keygen -E md5 -lf"
            if ! $OPT_SILENT ; then echo -n "$cmd ${arg##*/} : " ; fi
            eval "$cmd ${arg}"
        fi
        if openssl req -in "$arg" -text -noout >/dev/null 2>&1 ; then
            openssl req -in "$arg" -text -noout | sed -E '/^ *(Modulus:|[a-z0-9:]*$)/d'
        fi
        if openssl x509 -noout -fingerprint -md5 -inform pem -in "$arg" >/dev/null 2>&1 ; then
            openssl x509 -in "$arg" -text -noout | sed -E '/^ *(Modulus:|[a-z0-9:]*$)/d'
            openssl x509 -noout -fingerprint -md5 -inform pem -in "$arg"
            openssl x509 -noout -fingerprint -sha1 -inform pem -in "$arg"
            openssl x509 -noout -fingerprint -sha256 -inform pem -in "$arg"
        fi
    done

    if ! $OPT_SILENT ; then
        echo -e "${allFiles}\n"
    fi
}

function csrFiles() {
    local errormessage="$(\
        checkAndMaybeMoveExistingFiles "$(destDir cert)" \
            "$(destDir cert)/$(fileName certkey)" \
            "$(destDir cert)/$(fileName cert)" \
            "$(destDir cert)/$(fileName csr)" \
            "$(destDir cert)/$(fileName pem)" \
    )"
    if [[ -n "$errormessage" ]]; then # OPT_FORCE=false && files already exist
        >&2 echo "cert files (some) already exist! skipping operation..."
        return 2
    #else # FORCE=true ==> moving files to a tmpdir within $(destDir ssh)
    fi

    local encryptPrivateKey="-nodes" # define empty to encrypt private keys (if empty script will prompt for password!)
    #### create csr (certificate signing request)
    case "${OPT_CMD}" in
        'cert'|'selfsignedcert'|'csr')
            commonName="${ARGS[0]}"
            # BEWARE! ports are not allowed in subjectAltName aliases!
            # also the CN(=commonName ALWAYS should be included in the SAN(=SubjectAlternateNames))
            # subjectAltName="-reqexts SAN -config <(cat /etc/ssl/openssl.cnf <(printf \"[SAN]\nsubjectAltName=DNS:example.com,DNS:www.example.com,IP:10.0.2.240\"))"
            local sslConfig="-reqexts MYPART -config <(cat /etc/ssl/openssl.cnf <(printf \"[MYPART]\nsubjectAltName="
            sslConfig="${sslConfig}$(allSubjectAltNames "${ARGS[@]}")" # all ARGS
            if [[ $? -ne 0 ]]; then exit $? ; fi
            sslConfig="${sslConfig}\"))"
            # create a new private key AND a csr signed with it (same as for rootCA)
            local dontEncryptPrivateKey="-nodes" # define empty to encrypt private keys (if empty script will prompt for password!)
            format="openssl req -newkey rsa:${OPT_KEYBITS} ${dontEncryptPrivateKey} -keyout \"%s\" -out \"%s\" -subj \"/emailAddress=%s/C=%s/ST=%s/L=%s/O=%s/OU=%s/CN=%s\" %s"
            cmd=$(printf "$format" "$(destDir cert)/$(fileName certkey)" "$(destDir cert)/$(fileName csr)" "$OPT_EMAIL" "$OPT_COUNTRY" "$OPT_STATE" "$OPT_LOCATION" "$OPT_ORGANIZATION" "$OPT_ORGANIZATIONALUNIT" "$commonName" "$sslConfig")
            ;;
        *)
            >&2 echo "strange call to csrFiles() for OPT_CMD '${OPT_CMD}'"
            exit 11
            ;;
    esac
    if ! $OPT_SILENT ; then echo $cmd ; fi
    if $OPT_SILENT ; then
        eval $cmd >/dev/null
    else
        eval $cmd
        echo
    fi
}

##############################################################################
# sign a csr to create a cert
##############################################################################
function signCsr() {
    case "${OPT_CMD}" in
        'selfsignedcert')
            # create cert by signing the csr using the cert private key
                # Extensions of the csr are NOT transferred to the certificate and vice versa.
                # So we have to give $subjectAltName of the csr again.
                # Unfortunately the openssl options have different names than they had for creating the csr
                # so it should not be "-reqexts <sectionName> -config <xyz.cnf>" but on signing the cert it is "-extension <sectionName> -extfile <xyz.cnf>"
            local sslConfig="-reqexts MYPART -config <(cat /etc/ssl/openssl.cnf <(printf \"[MYPART]\nsubjectAltName="
            sslConfig="${sslConfig}$(allSubjectAltNames "${ARGS[@]}")" # all ARGS
            if [[ $? -ne 0 ]]; then exit $? ; fi
            sslConfig="${sslConfig}\"))"
            sslConfig="${sslConfig//-reqexts MYPART -config/-extensions MYPART -extfile}"
            format="openssl x509 -req -in \"%s\" -signkey \"%s\" -out \"%s\" -days ${OPT_DAYS} %s"
            cmd=$(printf "$format" "$(destDir cert)/$(fileName csr)" "$(destDir cert)/$(fileName certkey)" "$(destDir cert)/$(fileName cert)" "$sslConfig")
            if ! $OPT_SILENT ; then echo $cmd ; fi
            if $OPT_SILENT ; then
                eval $cmd >/dev/null
            else
                eval $cmd
                echo
            fi
            ;;
        'cert')
            local cacertFile=$(rootCAcertfile)
            local cakeyFile=$(rootCAkeyfile)
            # create cert by signing the csr using the rootCA private key
                # Extensions of the csr are NOT transferred to the certificate and vice versa.
                # So we have to give $subjectAltName of the csr again.
                # Unfortunately the openssl options have different names than they had for creating the csr
                # so it should not be "-reqexts <sectionName> -config <xyz.cnf>" but on signing the cert it is "-extension <sectionName> -extfile <xyz.cnf>"
            local sslConfig="-reqexts MYPART -config <(cat /etc/ssl/openssl.cnf <(printf \"[MYPART]\nsubjectAltName="
            sslConfig="${sslConfig}$(allSubjectAltNames "${ARGS[@]}")" # all ARGS
            if [[ $? -ne 0 ]]; then exit $? ; fi
            sslConfig="${sslConfig}\"))"
            sslConfig="${sslConfig//-reqexts MYPART -config/-extensions MYPART -extfile}"
            format="openssl x509 -req -in \"%s\" -CA \"%s\" -CAkey \"%s\" -CAcreateserial -out \"%s\" -days ${OPT_DAYS} %s"
            cmd=$(printf "$format" "$(destDir cert)/$(fileName csr)" "${cacertFile}" "${cakeyFile}" "$(destDir cert)/$(fileName cert)" "$sslConfig")
            if ! $OPT_SILENT ; then echo $cmd ; fi
            if $OPT_SILENT ; then
                eval $cmd >/dev/null
            else
                eval $cmd
                echo
            fi
            ;;
        'sign')
            >&2 echo "--cmd='sign' not implemented yet" ; exit 1
            ;;
        *)
            >&2 echo "strange call to signCsr() for OPT_CMD '${OPT_CMD}'"
            exit 11
            ;;
    esac
    rm ${OPT_BASEDIR}/${ROOTCA_FILEPREFIX}*.srl 2>/dev/null # do not quote!

    if ! $OPT_SILENT ; then
        printCertInfo "$(destDir cert)/$(fileName csr)" "$(destDir cert)/$(fileName cert)" "$(destDir cert)/$(fileName certkey)"
    fi

    ### optional also produce a pem file of the cert (in our case same as .cert but with .pem postfix)
    # s="openssl x509 -in %s -out %s" #  -inform der
    # pemCmd=$(printf "$s" "$destFolder/$certFilename" "$destFolder/$pemFilename")
    # echo $pemCmd
    # eval $pemCmd

}

##############################################################################
# create cert
##############################################################################
function certFiles() {

    ### output information of our results (csr and cert)
    checkCsrCmd="openssl req -in $destFolder/$csrFilename -text -noout"
    echo "==============================================================="
    echo "checkCsr: $checkCsrCmd"
    echo "==============================================================="
    eval $checkCsrCmd
    echo ""
    checkCertCmd="openssl x509 -text -noout -in $destFolder/$certFilename -certopt no_header,no_pubkey,no_sigdump"
    echo "========================================================================================================="
    echo "checkCert: $checkCertCmd"
    echo "========================================================================================================="
    eval $checkCertCmd

    ls -la $destFolder
}


##############################################################################
# create (self-signed) rootCA certificate
##############################################################################
function rootCAfiles {
    # local rootCaKeyAskForPassword="" # rootCaKeyAskForPassword=" -des3"
    # local rootCaKeyFile="$(destDir rootca)/$(fileName certkey)"
    # # # generate RSA private key
    # local cmd="openssl genrsa$rootCaKeyAskForPassword -out \"${rootCaKeyFile}\" $OPT_KEYBITS"
    # if ! $OPT_FORCE && [[ -s "${rootCaKeyFile}" ]]; then
    #     >&2 echo "rootCA keyfile already exists: ${rootCaKeyFile}"
    # elif $OPT_FORCE  && [[ -s "${rootCaKeyFile}" ]]; then
    #     local TMPDIR="$(mkTmpDir "$(destDir rootca)")"
    #     mv "${rootCaKeyFile}" "${rootCaKeyFile%.*}${CERT_KEY_POSTFIX}" "${TMPDIR}"
    #     echo $cmd
    #     eval $cmd
    # else
    #     echo $cmd
    #     eval $cmd
    # fi

    local errormessage="$(\
        checkAndMaybeMoveExistingFiles "$(destDir rootca)" \
            "$(destDir rootca)/$(fileName certkey)" \
            "$(destDir rootca)/$(fileName rootca)" \
            "$(destDir rootca)/$(fileName csr)" \
    )"
    if [[ -n "$errormessage" ]]; then # OPT_FORCE=false && files already exist
        >&2 echo "cert files (some) already exist! skipping operation..."
        return 2
    #else # FORCE=true ==> moving files to a tmpdir within $(destDir ssh)
    fi

    issuerName="${ARGS[0]}"
    commonName="${ARGS[1]}"
    # BEWARE! ports are not allowed in subjectAltName aliases!
    # also the CN(=commonName ALWAYS should be included in the SAN(=SubjectAlternateNames))
    # subjectAltName="-reqexts SAN -config <(cat /etc/ssl/openssl.cnf <(printf \"[SAN]\nsubjectAltName=DNS:example.com,DNS:www.example.com,IP:10.0.2.240\"))"
    local sslConfig="-reqexts MYPART -config <(cat /etc/ssl/openssl.cnf <(printf \"[MYPART]\nsubjectAltName="
    sslConfig="${sslConfig}$(allSubjectAltNames "${ARGS[@]:1}")" # all but the first ARG
    if [[ $? -ne 0 ]]; then exit $? ; fi
    # additional sslConfig constraints for rootCA
    sslConfig="${sslConfig}\nbasicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\nissuerAltName=URI:${issuerName}"
    sslConfig="${sslConfig}\"))"
    
    # create a new private key AND a csr signed with it (same as for cert and self-signed cert)
    local dontEncryptPrivateKey="-nodes" # define empty to encrypt private keys (if empty script will prompt for password!)
    format="openssl req -newkey rsa:${OPT_KEYBITS} ${dontEncryptPrivateKey} -keyout \"%s\" -out \"%s\" -subj \"/emailAddress=%s/C=%s/ST=%s/L=%s/O=%s/OU=%s/CN=%s\" %s"
    cmd=$(printf "$format" "$(destDir rootca)/$(fileName certkey)" "$(destDir rootca)/$(fileName csr)" "$OPT_EMAIL" "$OPT_COUNTRY" "$OPT_STATE" "$OPT_LOCATION" "$OPT_ORGANIZATION" "$OPT_ORGANIZATIONALUNIT" "$commonName" "$sslConfig")
    if ! $OPT_SILENT ; then echo $cmd ; fi
    if $OPT_SILENT ; then
        eval $cmd >/dev/null
    else
        eval $cmd
        echo
    fi

    # create rootCA by signing the csr using the same private key
        # Extensions of the csr are NOT transferred to the certificate and vice versa.
        # So we have to give $subjectAltName of the csr again.
        # Unfortunately the openssl options have different names than they had for creating the csr
        # so it should not be "-reqexts <sectionName> -config <xyz.cnf>" but on signing the cert it is "-extension <sectionName> -extfile <xyz.cnf>"
    sslConfig="${sslConfig//-reqexts MYPART -config/-extensions MYPART -extfile}"
    format="openssl x509 -req -in \"%s\" -signkey \"%s\" -out \"%s\" -days ${OPT_DAYS} %s"
    cmd=$(printf "$format" "$(destDir rootca)/$(fileName csr)" "$(destDir rootca)/$(fileName certkey)" "$(destDir rootca)/$(fileName rootca)" "$sslConfig")
    if ! $OPT_SILENT ; then echo $cmd ; fi
    if $OPT_SILENT ; then
        eval $cmd >/dev/null
    else
        eval $cmd
        echo
    fi

    if ! $OPT_SILENT ; then
        printCertInfo "$(destDir rootca)/$(fileName csr)" "$(destDir rootca)/$(fileName rootca)" "$(destDir rootca)/$(fileName certkey)"
    fi
}


function allSubjectAltNames() {
    # BEWARE! ports are not allowed in subjectAltName aliases!
    # also the CN(=commonName ALWAYS should be included in the SAN(=SubjectAlternateNames))
    # subjectAltName="-reqexts SAN -config <(cat /etc/ssl/openssl.cnf <(printf \"[SAN]\nsubjectAltName=DNS:example.com,DNS:www.example.com,IP:10.0.2.240\"))"
    # maybe bad idea but we use one ARGS loop to:
    #   a) check given parameters for validity (valid ip or domain) and
    #   b) add them to subjectAltName openssl cmd part used later
    local theSubjectAltNames=""
    local checksFailed="false"
    local checksFailedMessage=""
    local first="true"
    for ARG in "$@"; do
        valid_ip "$ARG"
        local isValidIp=$?
        valid_fqdn_or_wildcardDomain "${ARG//_/-}" # replace _ with -
        local isValidDN=$?
        if [[ ! isValidIp -eq 0 && ! isValidDN -eq 0 ]]; then
                checksFailedMessage+="$ARG is neither a valid domain nor IP\n"
                checksFailed="true"
        else
            if [[ "$first" != "true" ]]; then # if more than one subjectAltName separate by ','
                theSubjectAltNames+=","
            else
                first="false"
            fi
            if [[ isValidIp -eq 0 ]]; then
                theSubjectAltNames+="IP:$ARG"
            else
                theSubjectAltNames+="DNS:$ARG"
            fi
        fi
    done

    if [[ "$checksFailed" = "true" ]]; then
        >&2 echo -e "$checksFailedMessage"
        return 10
    fi

    printf "%s" "${theSubjectAltNames}"
}

##############################################################################
# helper functions
##############################################################################
function fileName() {
    if [[ -n $OPT_DESTFILESBASENAME ]]; then
        printf "%s" "${OPT_DESTFILESBASENAME}"
    else
        if [[ $OPT_CMD = "rootca" ]]; then
            printf "%s" "$ROOTCA_FILEPREFIX"
        fi
        if [[ -z $OPT_GROUP ]]; then
            printf "%s" "${OPT_BASENAME}"
        else
            printf "%s" "${OPT_GROUP_PF}${OPT_BASENAME}"
        fi
    fi
    case "$1" in
        'base'|'ssh')
            printf "%s" ""
            ;;
        'sshkey')
            printf "%s" "${SSH_KEY_POSTFIX}"
            ;;
        'sshpub')
            printf "%s" "${SSH_PUB_POSTFIX}"
            ;;
        'cert')
            printf "%s" "${CERT_POSTFIX}"
            ;;
        'certkey')
            printf "%s" "${CERT_KEY_POSTFIX}"
            ;;
        'csr')
            printf "%s" "${CSR_POSTFIX}"
            ;;
        'rootca')
            printf "%s" "${ROOTCA_POSTFIX}"
            ;;
        'pem')
            printf "%s" "${PEM_POSTFIX}"
            ;;
        *)
            >&2 echo "unknown fileName() first arg '$1'"
            exit 6
            ;;
    esac
}
function destDir() {
    if [[ -n $OPT_DESTDIR ]]; then
        if [[ ! -d $OPT_DESTDIR ]]; then >&2 echo "--destdir=${OPT_DESTDIR} does not exist!" ; exit 3 ; fi
        printf "%s" "${OPT_DESTDIR}"
        return
    fi
    case "$1" in
        'ssh')
            if [[ -z $OPT_GROUP ]]; then
                mkdir    -p "${OPT_BASEDIR}/ssh"
                printf "%s" "${OPT_BASEDIR}/ssh"
            else
                mkdir    -p "${OPT_BASEDIR}/${OPT_GROUP}/ssh"
                printf "%s" "${OPT_BASEDIR}/${OPT_GROUP}/ssh"
            fi
            ;;
        'selfsignedcert'|'cert'|'csr'|'sign')
            if [[ -z $OPT_GROUP ]]; then
                mkdir    -p "${OPT_BASEDIR}/certs"
                printf "%s" "${OPT_BASEDIR}/certs"
            else
                mkdir    -p "${OPT_BASEDIR}/${OPT_GROUP}/certs"
                printf "%s" "${OPT_BASEDIR}/${OPT_GROUP}/certs"
            fi
            ;;
        'rootca')
            mkdir    -p "${OPT_BASEDIR}"
            printf "%s" "${OPT_BASEDIR}"
            ;;
        *)
            >&2 echo "unknown destDir() first arg '$1'"
            exit 4
            ;;
    esac
}

function rootCAcertfile() {
    if [[ -n "${OPT_CACERTFILE}" ]]; then printf "%s" "${OPT_CACERTFILE}" ; return ; fi
    local foundRootCAs=$(find "$(destDir rootca)" -name "${ROOTCA_FILEPREFIX}*${ROOTCA_POSTFIX}" -maxdepth 1 2>/dev/null | wc -l)
    if [[ $foundRootCAs -eq 0 ]]; then >&2 echo "found no rootca_*${ROOTCA_POSTFIX} in basedir: '$(destDir rootca)/'" ; exit 14 ; fi
    if [[ $foundRootCAs -gt 1 ]]; then >&2 echo "found more than one rootca_*${ROOTCA_POSTFIX} in basedir: '$(destDir rootca)/'" ; exit 14 ; fi
    printf "%s" "$(find "$(destDir rootca)" -name "${ROOTCA_FILEPREFIX}*${ROOTCA_POSTFIX}" -maxdepth 1)"
}

function rootCAkeyfile() {
    if [[ -n "${OPT_CAKEYFILE}" ]]; then printf "%s" "${OPT_CAKEYFILE}" ; return ; fi
    local foundRootCAkeys=$(find "$(destDir rootca)" -name "${ROOTCA_FILEPREFIX}*${CERT_KEY_POSTFIX}" -maxdepth 1 2>/dev/null | wc -l)
    if [[ $foundRootCAkeys -eq 0 ]]; then >&2 echo "found no rootca_${CERT_KEY_POSTFIX} in basedir: '$(destDir rootca)/'" ; exit 14 ; fi
    if [[ $foundRootCAkeys -gt 1 ]]; then >&2 echo "found more than one rootca_${CERT_KEY_POSTFIX} in basedir: '$(destDir rootca)/'" ; exit 14 ; fi
    printf "%s" "$(find "$(destDir rootca)" -name "${ROOTCA_FILEPREFIX}*${CERT_KEY_POSTFIX}" -maxdepth 1)"
}

function mkTmpDir() {
    # create a tmp+timestamp dir inside given dir (or current dir)
    FILE_DATETIME="$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$1/tmp-${FILE_DATETIME}"
    printf "%s" "$1/tmp-${FILE_DATETIME}"
}

function checkAndMaybeMoveExistingFiles() {
    if [[ ! -d "$1" ]]; then >&2 echo "dir to create 'tmp-<date> in does not exist (has to be the first arg!)"; exit 23 ; fi
    local errormessage=$(checkExistingFiles "${@:2}")
    if [[ -n ${errormessage} ]]; then
        if ! $OPT_FORCE ; then
            echo "${errormessage}"
            return 2 # 2 meaning "skip calling operation"
        fi
        if $OPT_FORCE ; then
            local TMPDIR="$(mkTmpDir "$1")"
            if ! $OPT_SILENT ; then
                >&2 echo "FORCE=true ==> moving existing files from '$1/' to '${TMPDIR}/'"
            fi
            shift # remove first element of given args 
            eval mv "${@@Q}" "${TMPDIR@Q}" 2>/dev/null
        fi
    fi        
}
function checkExistingFiles() {
    # ok if files do NOT exist
    local existingFiles=()
    local f
    for f in "$@"; do
        if [[ -s "$f" ]]; then existingFiles+=("$f") ; fi
    done
    if [[ ${#existingFiles[@]} -gt 0 ]]; then
        for f in "${existingFiles[@]}"; do
            printf "%s\n" "$f"
        done
        return 1
    fi
    return 0
}
function mvFilesToNewTmpDir() {
    if [[ ! -d "$1" ]]; then >&2 echo "dir to create 'tmp-<date> in does not exist (has to be the first arg!)"; exit 22 ; fi
    local TMPDIR="$(mkTmpDir "$1")"
    >&2 echo "moving existing files from '$1/' to '${TMPDIR}/'"
    shift # remove first element of given args
    eval mv "${@@Q}" "${TMPDIR@Q}"
}

function valid_fqdn_or_wildcardDomain() {
    local domain="$1"
    if [[ $domain = "localhost" ]]; then
        return 0
    fi
    if [[ $domain =~ ^(\*\.)?[0-9]{1,}\.[0-9]{1,}(\.([0-9]{1,}))*(:[0-9]{4,5})?$ ]]; then 
        return 1 # this too much looks like a mislead IP
    fi
    if [[ $OPT_DO_NOT_ALLOW_SIMPLE_FQDNS = true ]]; then
        if [[ $domain =~ ^(\*\.)?[a-z0-9-]{1,}\.[a-z0-9-]{1,}(\.([a-z0-9-]{1,}))*(:[0-9]{4,5})?$ ]]; then 
            return 0
        else
            return 1
        fi
    else
        if [[ $domain =~ ^(\*\.)?[a-z0-9-]{1,}(\.([a-z0-9-]{1,}))*(:[0-9]{4,5})?$ ]]; then 
            return 0
        else
            return 1
        fi
    fi
}

function valid_ip() {
    local ip="$1"
    local stat=1
    local pureip
    local ipnr
    local port

    if [[ $ip =~ ^([0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3})(:([0-9]*))?$ ]]; then
        if [[ ${BASH_REMATCH[2]} = ":" ]]; then
            >&2 echo "ERROR: IP with colon but no port: $ip"
            return 1
        fi
        pureip=${BASH_REMATCH[1]}
        port=${BASH_REMATCH[3]}
        OLD_IFS=$IFS
        IFS='.'
        ipnr=($pureip)
        IFS=$OLD_IFS
        if [[ -n "$ip" && ${ipnr[0]} -le 255 && ${ipnr[1]} -le 255 && ${ipnr[2]} -le 255 && ${ipnr[3]} -le 255 ]]; then
            stat=0
        else
            >&2 echo "ERROR: IP part > 255 in $ip"
        fi
        if [[ $stat -eq 0 && -n "$port" ]]; then
            stat=1
            if [[ $port -gt 1024 && $port -le 65535 ]]; then
                stat=0
            else
                >&2 echo "ERROR: IP port not in range 1025-65535 in $ip"
            fi
        fi
    fi
    return $stat
}

##############################################################################
# original stuff
##############################################################################
    # openssl req -x509 -days 365 -newkey rsa:2048 -nodes -keyout example.key -out example.csr -subj \"/C=GB/ST=London/L=London/O=Globy/OU=IT Department/CN=example.com\"
    # -x509    = output a x509 structure (= self-signed certificate) instead of a certificate signing request (= .csr)
    # -days    = validity period of the certificate in days (only effective in combination with -x509 or x509 command)
    # -newkey  = generate a new private key along with the certificate and do not take an existing one via -key server.key
    #            use rsa for the private key with a length of 2048 bit
    # -nodes   = do not des encrypt the private key but leave it plain text
    # -keyout  = file to write the private key to usually the filename has no suffix at all or .key
    # -out     = filename of the Certificate signing request usually suffixed .csr
    #            the .csr is used to get signed by a CA (Certification Authority) like verizon or cacert.org
    #
    # you can also create a config file (e.g. req.conf) with all needed information and tell openssl to use it
    # [req]
    # distinguished_name = req_distinguished_name
    # x509_extensions = v3_req
    # prompt = no
    # [req_distinguished_name]
    # C = DE
    # ST = Bavaria
    # L = Munich
    # O = Personal Security
    # OU = IT Department
    # CN = www.company.com
    # emailAddress  = Dirk.Hoffmann.Spam@gmail.com
    # [v3_req]
    # keyUsage = keyEncipherment, dataEncipherment
    # extendedKeyUsage = serverAuth
    # subjectAltName = @alt_names
    # [alt_names]
    # DNS.1 = www.company.com
    # DNS.2 = company.com
    # DNS.3 = company.net
    #
    # and use a command like the following to use the config file:
    #    openssl req -x509 -days 365 -sha256 -newkey rsa:2048 -nodes -keyout cert.key -out cert.csr -config req.cnf -extensions 'v3_req'
    # please notice: to generate a .csr do NOT pass -x509 to openssl, to create a self-signed certificate do pass -x509
    #
    # on terminal
    # print a self-signed certificate with:
    #    openssl x509 -in certificate.crt -text -noout
    # print a signing request with:
    #     openssl req  -in certificate.csr -text -noout
    #
    # to create a self-signed certificate from a .csr:
    #    openssl x509 -req -days 365 -in server.csr -signkey server.key -out server.crt


##############################################################################
# parseCmdLineArgs
# from https://github.com/UrsaDK/getopts_long
##############################################################################
source "${SCRIPTDIR}/getopts_long/getopts_long.bash"

cmdLineOptsShort=() ; cmdLineOptsLong=()
cmdLineOptsLong+=('cmd:') ; OPT_CMD="cert"
cmdLineOptsShort+=('h')  ; cmdLineOptsLong+=('help')
cmdLineOptsShort+=('s')  ; cmdLineOptsLong+=('silent') ; OPT_SILENT=false
cmdLineOptsShort+=('i')  ; cmdLineOptsLong+=('info') ; OPT_INFO=false

cmdLineOptsLong+=('force') ; OPT_FORCE=false
cmdLineOptsLong+=('group:') ; OPT_GROUP=''
cmdLineOptsLong+=('basename:') ; OPT_BASENAME=''
cmdLineOptsLong+=('cacertfile:') ; OPT_CACERTFILE=''
cmdLineOptsLong+=('cakeyfile:') ; OPT_CAKEYFILE=''
cmdLineOptsLong+=('destdir:') ; OPT_DESTDIR=''
cmdLineOptsLong+=('destfilesbasename:') ; OPT_DESTFILESBASENAME=''
cmdLineOptsLong+=('basedir:') ; OPT_BASEDIR='certs'

cmdLineOptsLong+=('email:') ; OPT_EMAIL="$(git config --get user.email 2>/dev/null)"
cmdLineOptsLong+=('doNotAllowSimpleFQDNs') ; OPT_DO_NOT_ALLOW_SIMPLE_FQDNS=false
cmdLineOptsLong+=('keybits:') ; OPT_KEYBITS=2048
cmdLineOptsLong+=('country:') ; OPT_COUNTRY=""
cmdLineOptsLong+=('state:') ; OPT_STATE=""
cmdLineOptsLong+=('location:') ; OPT_LOCATION=""
cmdLineOptsLong+=('organization:') ; OPT_ORGANIZATION="COMPANY"
cmdLineOptsLong+=('organizationalUnit:') ; OPT_ORGANIZATIONALUNIT="IT Department"
cmdLineOptsLong+=('days:') ; OPT_DAYS=730

SHORTOPTSPEC=$(printf '%s\n' "$(IFS=''; printf '%s' "${cmdLineOptsShort[*]}")") # concat array elements without space
LONGOPTSPEC="${cmdLineOptsLong[*]}" # concat array elements with space
while getopts_long ":$SHORTOPTSPEC $LONGOPTSPEC" OPTKEY; do
    case ${OPTKEY} in
        'h'|'help')
            usage
            exit 0
            ;;
        's'|'silent')
            OPT_SILENT=true
            ;;
        'i'|'info')
            OPT_INFO=true ; OPT_CMD="info"
            ;;
        'force')
            OPT_FORCE=true
            ;;
        'cmd')
            if [[ "${OPTARG}" = "info" ]]; then OPT_INFO=true ; OPT_CMD="info"
            else
                OPT_CMD="${OPTARG}"
                if [[ ! $OPT_CMD =~ ^(ssh|rsa|selfsignedcert|cert|rootca|csr|sign)$ ]]; then
                    >&2 echo "--cmd=${OPTARG} not one of: (ssh|rsa|selfsignedcert|cert|rootca|csr|sign)"
                    exit 1
                fi
            fi
            ;;
        'group')
            OPT_GROUP="${OPTARG}"
            ;;
        'basename')
            OPT_BASENAME="${OPTARG%/}" # without trailing /
            ;;
        'cacertfile')
            OPT_CACERTFILE="${OPTARG}"
            ;;
        'cakeyfile')
            OPT_CAKEYFILE="${OPTARG}"
            ;;
        'destdir')
            OPT_DESTDIR="${OPTARG%/}" # without trailing /
            ;;
        'destfilesbasename')
            OPT_DESTFILESBASENAME="${OPTARG%/}" # without trailing /
            OPT_DESTFILESBASENAME="${OPTARG%.*}" # without any postfix
            OPT_DESTFILESBASENAME="${OPT_DESTFILESBASENAME##*/}" # filename only
            ;;
        'basedir')
            OPT_BASEDIR="${OPTARG%/}" # without trailing /
            ;;
        'email')
            OPT_EMAIL="${OPTARG}"
            ;;
        'doNotAllowSimpleFQDNs')
            OPT_DO_NOT_ALLOW_SIMPLE_FQDNS=true
            ;;
        'keybits')
            OPT_KEYBITS=${OPTARG}
            ;;
        'country')
            OPT_COUNTRY="${OPTARG}"
            ;;
        'state')
            OPT_STATE="${OPTARG}"
            ;;
        'location')
            OPT_LOCATION="${OPTARG}"
            ;;
        'organization')
            OPT_ORGANIZATION="${OPTARG}"
            ;;
        'organizationalUnit')
            OPT_ORGANIZATIONALUNIT="${OPTARG}"
            ;;
        'days')
            OPT_DAYS=${OPTARG}
            ;;
        '?')
            echo "INVALID OPTION: ${OPTARG}" >&2
            exit 1
            ;;
        ':')
            echo "MISSING ARGUMENT for option: ${OPTARG}" >&2
            exit 1
            ;;
        *)
            echo "UNIMPLEMENTED OPTION: ${OPTKEY}" >&2
            exit 1
            ;;
    esac
done

shift $(( OPTIND - 1 ))
set +u
[[ "${1}" == "--" ]] && shift
set -u
ARGS=( "$@" )

##############################################################################
# defaults
##############################################################################
OPT_GROUP_PF=$OPT_GROUP # PF = PostFixed
if [[ -n $OPT_GROUP ]]; then
    OPT_GROUP_PF="${OPT_GROUP}_"
fi
OPT_BASENAME_PF="${OPT_BASENAME}_" # PF = PostFixed
if [[ -z $OPT_BASENAME ]]; then
    case "$OPT_CMD" in
        'ssh'|'rsa')
            OPT_BASENAME="id_rsa"
            ;;
        'selfsignedcert'|'cert'|'csr'|'sign')
            OPT_BASENAME="cert"
            ;;
        'rootca')
            OPT_BASENAME="root"
            ;;
        'info')
            ;;
        *)
            >&2 echo "unknown cmd: '${OPT_CMD}'"
            ;;
    esac
fi
if [[ $OPT_CMD =~ selfsignedcert|cert|csr|sign|rootca ]]; then
    if [[ -z "$OPT_COUNTRY" || -z "$OPT_STATE" || -z "$OPT_LOCATION" ]]; then
        ipLocation=$(curl -s http://ipwhois.app/json/)
        if [[ -z "$OPT_COUNTRY" ]]; then OPT_COUNTRY="$(jq -r '.country_code' <<< "${ipLocation}" | sed 's/\([äöüÄÜÖ]\)/\1e/g;y/äöüÄÖÜ/aouAOU/;s/ß/ss/g')" ; fi
        if [[ -z "$OPT_STATE" ]]; then OPT_STATE="$(jq -r '.region' <<< "${ipLocation}" | sed 's/\([äöüÄÜÖ]\)/\1e/g;y/äöüÄÖÜ/aouAOU/;s/ß/ss/g')" ; fi
        if [[ -z "$OPT_LOCATION" ]]; then OPT_LOCATION="$(jq -r '.city' <<< "${ipLocation}" | sed 's/\([äöüÄÜÖ]\)/\1e/g;y/äöüÄÖÜ/aouAOU/;s/ß/ss/g')" ; fi
    fi
fi

CERT_POSTFIX=".cert"
CERT_KEY_POSTFIX=".key"
CSR_POSTFIX=".csr"
PEM_POSTFIX=".pem"
ROOTCA_POSTFIX=".ca"
ROOTCA_FILEPREFIX="rootca_"

SSH_KEY_POSTFIX=""
SSH_PUB_POSTFIX=".pub"

##############################################################################
# validations
##############################################################################
errormessage=()
for value in "${ARGS[@]}" "${OPT_GROUP}" "${OPT_BASENAME}" "${OPT_CACERTFILE}" "${OPT_CAKEYFILE}" "${OPT_DESTDIR}" "${OPT_BASEDIR}" "${OPT_EMAIL}" ; do
    if [[ $value =~ [' '] ]]; then
        errormessage+=("no arg or opt-value is allowed to contain spaces.")
    fi
done
if [[ ${#ARGS[@]} -eq 0 && ! "${OPT_CMD}" =~ ssh|rsa ]]; then
    if [[ "${OPT_CMD}" =~ info ]]; then errormessage+=("no args given. at least path to a cert necessary")
    elif [[ "${OPT_CMD}" =~ selfsignedcert|cert|csr ]]; then errormessage+=("no args given. at least CN/CommonName/Domain necessary") ; fi
fi
if [[ ${#ARGS[@]} -lt 2 && ! ${OPT_INFO} = true && ! "${OPT_CMD}" =~ ssh|rsa|sign ]]; then
    if [[ "${OPT_CMD}" =~ rootca ]]; then errormessage+=("no args given. at least issuer and CN/CommonName/Domain necessary") ; fi
fi
case "${OPT_CMD}" in
    'ssh'|'rsa')
        if [[ -z $OPT_EMAIL ]]; then
            errormessage+=("no --email given to use as comment inside ssh/rsa pub key.")
        fi
esac

if [[ "${OPT_CMD}" = "info" ]]; then
    for f in "${ARGS[@]}"; do
        if [[ ! -s "$f" ]]; then errormessage+=("file: '$f' does not exist") ; fi
    done
fi
if [[ -n "${OPT_CACERTFILE}" || -n "${OPT_CAKEYFILE}" ]]; then
    if [[ -z "${OPT_CACERTFILE}" || -z "${OPT_CAKEYFILE}" ]]; then errormessage+=("--cacertfile and --cakeyfile have to be given both.") ; fi
    if [[ -n "${OPT_CACERTFILE}" && ! -s "${OPT_CACERTFILE}" ]]; then errormessage+=("--cacertfile '${OPT_CACERTFILE}' does not exist or is empty.") ; fi
    if [[ -n "${OPT_CAKEYFILE}" &&! -s "${OPT_CAKEYFILE}" ]]; then errormessage+=("--cakeyfile '${OPT_CAKEYFILE}' does not exist or is empty.") ; fi
elif [[ ${OPT_CMD} =~ csr|cert|sign ]]; then
    rootCAcertfile
    rootCAkeyfile
fi

# exit on any errors
if [[ "${#errormessage[@]}" -gt 0 ]]; then
    >&2 echo "cmd line opts and/or args errors:"
    for e in "${errormessage[@]}"; do
        >&2 echo "$e"
    done
    exit 99
fi

##############################################################################
##############################################################################
##############################################################################
# finally do it
main "$@"
