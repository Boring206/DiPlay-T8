package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.net.Socket
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterPlayerStreamTest {
    @Test fun playerStopsVideoRetriesAfterEventReadinessAndResumesPreviousMapContent() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("xcertplay_airplay",0).edit().clear().putBoolean("cluster_map_enabled",true).commit()
        val initial = CarPlayClusterDisplay.Content.TURN_CARD.url
        val config = AirPlayConfig("test","test","","1",AirPlayDisplayConfig(800,480),
            cluster=AirPlayDisplayConfig(1600,600,initialUrl=initial))
        val controller = CarPlayController(app,CarPlayRuntimeConfig(mfiTarget=MfiTarget.LOCAL,
            transport=CarPlayTransport.WIRED,identification=Iap2IdentificationConfig(name="test",modelIdentifier="test",
                manufacturer="test",serialNumber="test",firmwareVersion="1",hardwareVersion="1",carPlayUsbInterfaceNumber=3)),
            config,AirPlayIdentity.generate(),PairingStore(),object:AirPlaySessionListener{},object:AirPlayMediaHandler{}, {})
        val session = AirPlaySession(Socket(),config,AirPlayIdentity.generate(),PairingStore(),null,
            object:AirPlaySessionListener{},object:AirPlayMediaHandler {
                override fun onScreen(session:AirPlaySession,type:Int,stream:Map<String,Any?>)=1
            })
        val bytes=ByteArrayOutputStream()
        val event=object:Socket() { override fun getOutputStream()=bytes }
        fun field(name:String,value:Any?) { session.javaClass.getDeclaredField(name).apply { isAccessible=true }.set(session,value) }
        fun apply() { controller.javaClass.getDeclaredMethod("applyClusterUi",Boolean::class.javaPrimitiveType)
            .apply { isAccessible=true }.invoke(controller,true) }
        fun setup() { session.javaClass.getDeclaredMethod("handleStreams",List::class.java).apply { isAccessible=true }
            .invoke(session,listOf(mapOf("type" to 111L))) }
        controller.javaClass.getDeclaredField("activeSession").apply { isAccessible=true }.set(controller,session)
        try {
            setup()
            ClusterPlayerSettings.save(app,true)
            apply() // no event connection: must retry rather than claiming delivery
            field("eventSocket",event); field("eventCipher",ControlCipher(ByteArray(32),ByteArray(32)))
            apply(); apply() // identical policy must not send stopUI repeatedly
            ClusterPlayerSettings.save(app,false)
            apply()
            val plaintext=ControlCipher(ByteArray(32),ByteArray(32)).decrypt(bytes.toByteArray()).data
            val messages=RtspMessage.parseMessages(plaintext).messages.map { BplistCodec.decode(it.body) as Map<*,*> }
            assertEquals(listOf("stopUI","showUI","forceKeyFrame"),messages.map { it["type"] })
            assertEquals(initial,(messages[1]["params"] as Map<*,*>)["url"])
            assertEquals(initial,session.clusterUrl())
        } finally {
            session.close(); controller.close(); assertTrue(controller.awaitClosed(2_000))
            app.getSharedPreferences("xcertplay_airplay",0).edit().clear().commit()
        }
    }
}
