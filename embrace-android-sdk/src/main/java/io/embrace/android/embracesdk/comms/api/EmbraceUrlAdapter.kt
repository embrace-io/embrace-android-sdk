package io.embrace.android.embracesdk.comms.api

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

internal class EmbraceUrlAdapter : TypeAdapter<EmbraceUrl>() {

    override fun write(jsonWriter: JsonWriter, embraceUrl: EmbraceUrl?) {
        jsonWriter.run {
            beginObject()
            name("url").value(embraceUrl?.toString())
            endObject()
        }
    }

    override fun read(jsonReader: JsonReader): EmbraceUrl? {
        var embraceUrl: EmbraceUrl? = null

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            if (jsonReader.nextName() == "url") {
                embraceUrl = EmbraceUrl.create(jsonReader.nextString())
            }
        }
        jsonReader.endObject()

        return embraceUrl
    }
}
