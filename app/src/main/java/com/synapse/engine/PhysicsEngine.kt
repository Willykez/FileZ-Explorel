package com.synapse.engine

class PhysicsEngine(private val data: DataManager) {
    private val gravityConstant = 0.2f
    private val friction = 0.92f

    // Centers of mass for each type to keep it O(N)
    private val typeCentersX = mutableMapOf<Int, Float>()
    private val typeCentersY = mutableMapOf<Int, Float>()
    private val typeCounts = mutableMapOf<Int, Int>()

    fun update() {
        val count = data.count
        if (count == 0) return

        // Step 1: Calculate centers of mass per type - O(N)
        typeCentersX.clear()
        typeCentersY.clear()
        typeCounts.clear()

        synchronized(data) {
            for (i in 0 until count) {
                val t = data.types[i]
                typeCentersX[t] = typeCentersX.getOrDefault(t, 0f) + data.x[i]
                typeCentersY[t] = typeCentersY.getOrDefault(t, 0f) + data.y[i]
                typeCounts[t] = typeCounts.getOrDefault(t, 0) + 1
            }

            for (t in typeCounts.keys) {
                val c = typeCounts[t]!!
                typeCentersX[t] = typeCentersX[t]!! / c
                typeCentersY[t] = typeCentersY[t]!! / c
            }

            // Step 2: Update nodes - O(N)
            for (i in 0 until count) {
                // Gravity towards center (0,0)
                val distToCenter = Math.sqrt((data.x[i] * data.x[i] + data.y[i] * data.y[i]).toDouble()).toFloat()
                if (distToCenter > 1f) {
                    data.vx[i] -= (data.x[i] / distToCenter) * gravityConstant
                    data.vy[i] -= (data.y[i] / distToCenter) * gravityConstant
                }

                // Attraction towards its own type center - O(1) per node
                val t = data.types[i]
                val tx = typeCentersX[t]!!
                val ty = typeCentersY[t]!!
                data.vx[i] += (tx - data.x[i]) * 0.02f
                data.vy[i] += (ty - data.y[i]) * 0.02f

                // Apply velocity
                data.x[i] += data.vx[i]
                data.y[i] += data.vy[i]
                data.vx[i] *= friction
                data.vy[i] *= friction
            }
        }
    }
}
