package com.example.robotarmcontroller.data.model

data class CycleInfo(
    val index: Int,
    val active: Boolean,
    val running: Boolean,
    val currentPose: Int,
    val poseCount: Int,
    val loopCount: Int,
    val maxLoops: Int,
    val activeGroupId: Int
)