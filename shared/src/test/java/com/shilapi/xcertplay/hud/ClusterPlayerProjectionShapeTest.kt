package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.airplay.ClusterPlayerSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],manifest=Config.NONE)
class ClusterPlayerProjectionShapeTest {
    @Test fun playerRespectsSavedHalfAndFullModesAndStillReleases() {
        val app=RuntimeEnvironment.getApplication()
        val preferences=app.getSharedPreferences("xcertplay_airplay",0)
        preferences.edit().clear().putBoolean("cluster_map_enabled",true).commit()
        BydOutputSettings.setClusterProjectionShape(app,BydClusterProjectionShape.HALF)
        try {
            ClusterPlayerSettings.save(app,true)
            assertEquals(BydDiLink3ClusterMode.Mode.PROJECTION,BydDiLink3ClusterOutput.requestedMode(app,true,false))
            assertEquals(17,BydDiLink3ClusterOutput.requestedMode(app,true,false)!!.info)
            assertEquals(BydClusterProjectionShape.HALF,BydOutputSettings.clusterProjectionShape(app))
            assertNull(BydDiLink3ClusterOutput.requestedMode(app,false,false))
            assertEquals(BydDiLink3ClusterMode.Mode.SIMPLE_NAVIGATION,BydDiLink3ClusterOutput.requestedMode(app,false,true))
            BydOutputSettings.setClusterProjectionShape(app,BydClusterProjectionShape.FULL)
            assertEquals(BydDiLink3ClusterMode.Mode.FULL_PROJECTION,BydDiLink3ClusterOutput.requestedMode(app,true,false))
            BydOutputSettings.setClusterProjectionShape(app,BydClusterProjectionShape.HALF)
            ClusterPlayerSettings.save(app,false)
            assertEquals(BydDiLink3ClusterMode.Mode.PROJECTION,BydDiLink3ClusterOutput.requestedMode(app,true,false))
        } finally { preferences.edit().clear().commit() }
    }
}
