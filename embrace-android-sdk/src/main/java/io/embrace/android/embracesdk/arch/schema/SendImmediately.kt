import io.embrace.android.embracesdk.arch.schema.EmbraceAttributeKey
import io.embrace.android.embracesdk.arch.schema.FixedAttribute

internal object SendImmediately : FixedAttribute {
    override val key = EmbraceAttributeKey(id = "send_immediately")
    override val value: String = "true"
}
