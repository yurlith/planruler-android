package com.planruler.document.android

import com.planruler.document.api.CaptureContainerInput
import com.planruler.document.api.DepthDecodeStatus
import com.planruler.document.api.DepthDecodeFailure
import com.planruler.document.api.DepthEncoding
import com.planruler.document.api.DepthPayloadLocation
import com.planruler.document.api.DepthStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

class DepthContainerDecoderTest {
    private val fakeRasterDecoder = DepthRasterDecoder { bytes ->
        when (bytes.toString(Charsets.US_ASCII)) {
            "depth" -> DecodedDepthRaster(2, 2, floatArrayOf(0f, 0.5f, 0.75f, 1f), 16, "fake")
            "confidence" -> DecodedDepthRaster(2, 2, floatArrayOf(1f, 0.5f, 0.75f, 1f), 8, "fake")
            else -> null
        }
    }

    @Test fun `gdepth base64 is decoded to one metric representation`() {
        val depth = Base64.getEncoder().encodeToString("depth".toByteArray())
        val confidence = Base64.getEncoder().encodeToString("confidence".toByteArray())
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:GDepth="http://ns.google.com/photos/1.0/depthmap/">
              <rdf:Description GDepth:Format="RangeInverse" GDepth:Near="1" GDepth:Far="9"
                GDepth:Units="m" GDepth:Mime="image/png" GDepth:MeasureType="OpticRay"
                GDepth:Data="$depth" GDepth:Confidence="$confidence" />
            </x:xmpmeta>
        """.trimIndent().toByteArray()

        val outcome = DepthContainerDecoder(fakeRasterDecoder).decode(xmp, CaptureContainerInput())

        assertEquals(DepthDecodeStatus.DECODED_METRIC, outcome.report.status)
        assertEquals(DepthPayloadLocation.XMP_BASE64, outcome.report.payloadLocation)
        assertTrue(DepthStandard.GDEPTH in outcome.container.depthStandards)
        val map = requireNotNull(outcome.report.map)
        assertEquals(1f, map.depthMeters[0], 0.0001f)
        assertEquals(1.8f, map.depthMeters[1], 0.0001f)
        assertEquals(9f, map.depthMeters[3], 0.0001f)
        assertNotNull(map.confidence)
    }

    @Test fun `dynamic depth directory is sliced from file end independent of primary format`() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:DepthMap="http://ns.google.com/photos/dd/1.0/depthmap"
              xmlns:Container="http://ns.google.com/photos/dd/1.0/container"
              xmlns:Item="http://ns.google.com/photos/dd/1.0/item">
              <rdf:Description DepthMap:Format="RangeLinear" DepthMap:Near="2" DepthMap:Far="6"
                DepthMap:Units="Meters" DepthMap:DepthURI="depth.bin" DepthMap:ConfidenceURI="confidence.bin" />
              <Container:Directory><rdf:Seq>
                <rdf:li rdf:parseType="Resource" Item:Mime="image/heif" Item:Length="0" />
                <rdf:li rdf:parseType="Resource" Item:Mime="image/png" Item:Length="5" Item:DataURI="depth.bin" />
                <rdf:li rdf:parseType="Resource" Item:Mime="image/png" Item:Length="10" Item:DataURI="confidence.bin" />
              </rdf:Seq></Container:Directory>
            </x:xmpmeta>
        """.trimIndent().toByteArray()
        val file = xmp + "depth".toByteArray() + "confidence".toByteArray()

        val outcome = DepthContainerDecoder(fakeRasterDecoder).decode(file, CaptureContainerInput())

        assertEquals(DepthDecodeStatus.DECODED_METRIC, outcome.report.status)
        assertEquals(DepthPayloadLocation.CONCATENATED_CONTAINER_ITEM, outcome.report.payloadLocation)
        val map = requireNotNull(outcome.report.map)
        assertEquals(2f, map.depthMeters[0], 0.0001f)
        assertEquals(4f, map.depthMeters[1], 0.0001f)
        assertEquals(6f, map.depthMeters[3], 0.0001f)
    }

    @Test fun `decoded grayscale without metric units remains relative`() {
        val depth = Base64.getEncoder().encodeToString("depth".toByteArray())
        val xmp = """
            <x:xmpmeta xmlns:GDepth="http://ns.google.com/photos/1.0/depthmap/">
              <rdf:Description GDepth:Format="RangeLinear" GDepth:Near="1" GDepth:Far="255"
                GDepth:Units="None" GDepth:Data="$depth" />
            </x:xmpmeta>
        """.trimIndent().toByteArray()

        val outcome = DepthContainerDecoder(fakeRasterDecoder).decode(xmp, CaptureContainerInput())

        assertEquals(DepthDecodeStatus.DECODED_RELATIVE, outcome.report.status)
        assertEquals(null, outcome.report.map)
    }

    @Test fun `invalid base64 is reported as malformed and never accepted as depth`() {
        val xmp = """
            <x:xmpmeta xmlns:GDepth="http://ns.google.com/photos/1.0/depthmap/">
              <rdf:Description GDepth:Format="RangeLinear" GDepth:Near="1" GDepth:Far="5"
                GDepth:Data="not-base64!" />
            </x:xmpmeta>
        """.trimIndent().toByteArray()

        val report = DepthContainerDecoder(fakeRasterDecoder).decode(xmp, CaptureContainerInput()).report

        assertEquals(DepthDecodeStatus.MALFORMED_METADATA, report.status)
        assertEquals(DepthDecodeFailure.INVALID_BASE64, report.failure)
        assertEquals(null, report.map)
    }

    @Test fun `extended jpeg xmp chunks are reconstructed by guid and offset`() {
        val xml = "<x:xmpmeta xmlns:GDepth=\"http://ns.google.com/photos/1.0/depthmap/\"><GDepth:Data>depth</GDepth:Data></x:xmpmeta>"
            .toByteArray()
        val split = xml.size / 2
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte()) +
            extendedApp1(xml.copyOfRange(split, xml.size), xml.size, split) +
            extendedApp1(xml.copyOfRange(0, split), xml.size, 0) +
            byteArrayOf(0xff.toByte(), 0xd9.toByte())

        val packets = XmpPacketExtractor.extract(jpeg)

        assertTrue(packets.any { it.contains("<GDepth:Data>depth</GDepth:Data>") })
    }

    @Test fun `sixteen bit grayscale png keeps word precision`() {
        val png = grayscale16Png(intArrayOf(0, 32768, 65535), width = 3, height = 1)

        val raster = requireNotNull(PngGrayscaleDecoder.decode(png))

        assertEquals(16, raster.bitDepth)
        assertEquals(0f, raster.normalizedSamples[0], 0f)
        assertEquals(0.5000076f, raster.normalizedSamples[1], 0.000001f)
        assertEquals(1f, raster.normalizedSamples[2], 0f)
    }

    private fun extendedApp1(chunk: ByteArray, fullLength: Int, offset: Int): ByteArray {
        val header = "http://ns.adobe.com/xmp/extension/\u0000".toByteArray(Charsets.US_ASCII)
        val guid = "0123456789ABCDEF0123456789ABCDEF".toByteArray(Charsets.US_ASCII)
        val body = header + guid + u32(fullLength) + u32(offset) + chunk
        val length = body.size + 2
        return byteArrayOf(0xff.toByte(), 0xe1.toByte(), (length ushr 8).toByte(), length.toByte()) + body
    }

    private fun grayscale16Png(values: IntArray, width: Int, height: Int): ByteArray {
        val raw = ByteArrayOutputStream().apply {
            for (y in 0 until height) {
                write(0)
                for (x in 0 until width) {
                    val value = values[y * width + x]
                    write(value ushr 8)
                    write(value)
                }
            }
        }.toByteArray()
        val compressed = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output).use { it.write(raw) }
        }.toByteArray()
        val ihdr = u32(width) + u32(height) + byteArrayOf(16, 0, 0, 0, 0)
        return PNG_SIGNATURE + chunk("IHDR", ihdr) + chunk("IDAT", compressed) + chunk("IEND", byteArrayOf())
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()
        return u32(data.size) + typeBytes + data + u32(crc)
    }

    private fun u32(value: Int) = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}
