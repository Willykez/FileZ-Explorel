package com.synapse.engine

class DataManager(initialCapacity: Int = 1000) {
    var count = 0
    private var capacity = initialCapacity

    var fileIds = IntArray(capacity)
    var sizes = LongArray(capacity)
    var dates = LongArray(capacity)
    var types = IntArray(capacity)
    var x = FloatArray(capacity)
    var y = FloatArray(capacity)
    var vx = FloatArray(capacity)
    var vy = FloatArray(capacity)
    var names = arrayOfNulls<String>(capacity)
    var paths = arrayOfNulls<String>(capacity)

    @Synchronized
    fun addFile(name: String, path: String, size: Long, date: Long, type: Int) {
        if (count >= capacity) {
            grow()
        }
        names[count] = name
        paths[count] = path
        sizes[count] = size
        dates[count] = date
        types[count] = type
        fileIds[count] = count
        x[count] = (Math.random() * 2000 - 1000).toFloat()
        y[count] = (Math.random() * 2000 - 1000).toFloat()
        vx[count] = 0f
        vy[count] = 0f
        count++
    }

    private fun grow() {
        capacity *= 2
        fileIds = fileIds.copyOf(capacity)
        sizes = sizes.copyOf(capacity)
        dates = dates.copyOf(capacity)
        types = types.copyOf(capacity)
        x = x.copyOf(capacity)
        y = y.copyOf(capacity)
        vx = vx.copyOf(capacity)
        vy = vy.copyOf(capacity)
        names = names.copyOf(capacity)
        paths = paths.copyOf(capacity)
    }
}
