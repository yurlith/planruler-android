package com.planruler.document.android

import com.planruler.document.api.DepthEncoding
import com.planruler.document.api.DepthStandard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerSignalScannerTest {
    @Test fun detectsMetricGDepthPayloadAndConfidence() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/"
              xmlns:GDepth="http://ns.google.com/photos/1.0/depthmap/">
              <rdf:Description GDepth:Format="RangeInverse" GDepth:Near="0.4"
                GDepth:Far="8.5" GDepth:Units="m" GDepth:Data="AAEC"
                GDepth:Confidence="AQID" />
            </x:xmpmeta>
        """.trimIndent().toByteArray()

        val result = ContainerSignalScanner.scan(xmp, byteArrayOf())

        assertTrue(DepthStandard.GDEPTH in result.depthStandards)
        assertTrue(result.depthPayloadConfirmed)
        assertTrue(result.confidencePayloadConfirmed)
        assertTrue(result.hasMetricDepth)
        assertEquals(DepthEncoding.RANGE_INVERSE, result.depthEncoding)
        assertEquals(0.4, result.depthNear!!, 0.0)
        assertEquals(8.5, result.depthFar!!, 0.0)
    }

    @Test fun detectsDynamicDepthArPhotoSurveySignals() {
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/" xmlns:DepthMap="http://ns.google.com/photos/dd/1.0/">
              <rdf:Description Profile="ARPhoto" DepthMap:Units="Meters"
                DepthMap:DepthMap="payload" CameraPose="pose" ImagingModel="pinhole"
                Device:Planes="planes" Item:Semantic="Depth" />
            </x:xmpmeta>
        """.trimIndent().toByteArray()

        val result = ContainerSignalScanner.scan(xmp, byteArrayOf())

        assertTrue(DepthStandard.DYNAMIC_DEPTH in result.depthStandards)
        assertTrue(DepthStandard.ANDROID_DEPTH_JPEG in result.depthStandards)
        assertTrue(result.hasMetricDepth)
        assertTrue(result.cameraPosePresent)
        assertTrue(result.worldPlanesPresent)
        assertTrue(result.imagingModelPresent)
    }

    @Test fun motionPhotoRequiresBothDeclarationAndAppendedVideoBox() {
        val declaration = "<rdf:Description GCamera:MotionPhoto=\"1\" xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"/>"

        assertFalse(ContainerSignalScanner.scan(declaration.toByteArray(), byteArrayOf()).motionPhotoVideoConfirmed)
        assertTrue(
            ContainerSignalScanner.scan(
                declaration.toByteArray(),
                byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray(),
            ).motionPhotoVideoConfirmed,
        )
    }

    @Test fun depthDeclarationWithoutPayloadIsNotMetricDepth() {
        val xmp = """
            <x:xmpmeta xmlns:GDepth="http://ns.google.com/photos/1.0/depthmap/">
              <rdf:Description GDepth:Units="m" />
            </x:xmpmeta>
        """.trimIndent().toByteArray()

        val result = ContainerSignalScanner.scan(xmp, byteArrayOf())

        assertTrue(DepthStandard.GDEPTH in result.depthStandards)
        assertFalse(result.depthPayloadConfirmed)
        assertFalse(result.hasMetricDepth)
    }
}
