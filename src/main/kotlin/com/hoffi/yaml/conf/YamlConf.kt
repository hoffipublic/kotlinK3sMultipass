@file:UseSerializers(FileAsPath::class)

package com.hoffi.conf.yaml

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

@Serializable
class YamlConf {
    var targetEnv: String = "default"
    var TargetENV: String = "Default"
    var IP_HOST: String = "unknown"
    var NIC_HOST: String = "unknown"
    var IP_DNSSERVER: String = "unknown"
    var DIR: DIRS = DIRS()

    val USING_DNSMASQ_ON_HOST: Boolean = true
    val DEPLOY_LOCALPATHSTORAGE: Boolean = true
    val DEPLOY_LONGHORN: Boolean = false
    val NAMESPACE_LONGHORN: String = ""
    val DEPLOY_MONITORING: Boolean = false
    val NAMESPACE_MONITORING: String = ""
    val DEPLOY_VAULT: Boolean = false
    val NAMESPACE_VAULT: String = ""
    val HOST_KUBECONFIG_FILE: File = File("")
    val MULTIPASSBASEOS: String = "lts"
    val K3SNODECOUNT: Int = 1
    val K3SNODENAMEPREFIX: String = "k3snode"
    val K3SNODES: MutableList<K3SNODE> = mutableListOf()
    val MYCLUSTER_DOMAIN: String = ""
    val CERT_FILENAME: String = ""
    val CERT_KEY_FILENAME: String = ""

    @kotlinx.serialization.Transient
    val tmpMap = mutableMapOf<String, Any>()

    @Serializable
    class DIRS {
        // these are created in AppKt.init()
        var HOME: File = File(System.getenv("HOME"))
        var TMPDIR: File = File("tmp/default")
        var GENDIR: File = File("generated/default")
        var CONFDIR: File = File("conf")
    }
    @Serializable
    data class K3SNODE(
        val name: String = "default"
    )
}

object FileAsPath : KSerializer<File> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("File", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: File) = encoder.encodeString(value.path)
    override fun deserialize(decoder: Decoder): File = File(decoder.decodeString())
}
