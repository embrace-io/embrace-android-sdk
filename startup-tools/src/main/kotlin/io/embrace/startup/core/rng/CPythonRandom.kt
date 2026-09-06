package io.embrace.startup.core.rng

/**
 * A bit-exact port of CPython's `random.Random` for the operations the statistics library uses:
 * `random()`, `getrandbits()`, `choice()` and `shuffle()`, seeded with a non-negative integer.
 *
 * Why this exists rather than `kotlin.random.Random`: the method of record is a seeded two-stage
 * cluster bootstrap and a cluster permutation test, and the strongest available proof that the port
 * carried that method over intact is reproducing the Python's confidence bounds and p-values to the
 * last digit. That is only possible if every resampling draw is identical, which means the generator
 * must be identical - CPython's Mersenne Twister with its own seeding, its own 53-bit double
 * construction and its own rejection sampling. `kotlin.random.Random` is a different generator
 * (XorWow) with no stability guarantee across stdlib versions.
 *
 * Sources, verified against CPython's `Modules/_randommodule.c` and `Lib/random.py`:
 * - seeding: `seed(n)` for an int takes `abs(n)`, splits it into 32-bit little-endian words, and
 *   calls `init_by_array` (Matsumoto & Nishimura, 2002 init);
 * - `random()`: two 32-bit draws `a >>> 5`, `b >>> 6`, then `(a * 67108864.0 + b) / 2^53`;
 * - `getrandbits(k)` for k ≤ 32: the TOP k bits of one draw (`x >>> (32 - k)`);
 * - `_randbelow(n)`: rejection sampling on `getrandbits(n.bit_length())`;
 * - `shuffle(x)`: Fisher–Yates from the end, `j = _randbelow(i + 1)`;
 * - `choice(seq)`: `seq[_randbelow(len(seq))]`.
 *
 * Only the subset the Python statistics code touches is implemented. Adding `randint`, `gauss`
 * or `sample` would need their exact CPython algorithms too - do not approximate them.
 */
class CPythonRandom(seed: Long) {

    private val mt = IntArray(N)
    private var index = N

    init {
        require(seed >= 0) { "CPython seeds negative ints via abs(); pass the absolute value" }
        initByArray(seedToKey(seed))
    }

    /** Equivalent of `random.random()`: a double in [0, 1) built from 53 random bits. */
    fun random(): Double {
        val a = (genrandUint32() ushr 5).toLong() // 27 bits
        val b = (genrandUint32() ushr 6).toLong() // 26 bits
        return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0)
    }

    /** Equivalent of `random.getrandbits(k)` for 1 ≤ k ≤ 32. */
    fun getRandBits(k: Int): Long {
        require(k in 1..32) { "only k <= 32 is implemented (a single 32-bit draw); got $k" }
        return (genrandUint32().toLong() and 0xFFFFFFFFL) ushr (32 - k)
    }

    /** Equivalent of `random._randbelow(n)`: uniform integer in [0, n) by rejection sampling. */
    fun randBelow(n: Int): Int {
        require(n > 0) { "n must be positive" }
        val k = 32 - Integer.numberOfLeadingZeros(n) // n.bit_length()
        var r = getRandBits(k)
        while (r >= n) {
            r = getRandBits(k)
        }
        return r.toInt()
    }

    /** Equivalent of `random.choice(seq)`. */
    fun <T> choice(seq: List<T>): T {
        require(seq.isNotEmpty()) { "cannot choose from an empty sequence" }
        return seq[randBelow(seq.size)]
    }

    /** Equivalent of `random.shuffle(x)`: in place, Fisher–Yates from the end. */
    fun <T> shuffle(x: MutableList<T>) {
        for (i in x.size - 1 downTo 1) {
            val j = randBelow(i + 1)
            val tmp = x[i]
            x[i] = x[j]
            x[j] = tmp
        }
    }

    // ---- MT19937 --------------------------------------------------------------------------------

    private fun initGenrand(s: Int) {
        mt[0] = s
        for (i in 1 until N) {
            val prev = mt[i - 1]
            mt[i] = 1812433253 * (prev xor (prev ushr 30)) + i
        }
        index = N
    }

    private fun initByArray(key: IntArray) {
        initGenrand(19650218)
        var i = 1
        var j = 0
        var k = maxOf(N, key.size)
        while (k > 0) {
            val prev = mt[i - 1]
            mt[i] = (mt[i] xor ((prev xor (prev ushr 30)) * 1664525)) + key[j] + j
            i++
            j++
            if (i >= N) {
                mt[0] = mt[N - 1]
                i = 1
            }
            if (j >= key.size) {
                j = 0
            }
            k--
        }
        k = N - 1
        while (k > 0) {
            val prev = mt[i - 1]
            mt[i] = (mt[i] xor ((prev xor (prev ushr 30)) * 1566083941)) - i
            i++
            if (i >= N) {
                mt[0] = mt[N - 1]
                i = 1
            }
            k--
        }
        mt[0] = 0x80000000.toInt()
        index = N
    }

    private fun genrandUint32(): Int {
        if (index >= N) {
            twist()
        }
        var y = mt[index++]
        y = y xor (y ushr 11)
        y = y xor ((y shl 7) and 0x9d2c5680.toInt())
        y = y xor ((y shl 15) and 0xefc60000.toInt())
        y = y xor (y ushr 18)
        return y
    }

    private fun twist() {
        for (kk in 0 until N - M) {
            val y = (mt[kk] and UPPER_MASK) or (mt[kk + 1] and LOWER_MASK)
            mt[kk] = mt[kk + M] xor (y ushr 1) xor mag01(y)
        }
        for (kk in N - M until N - 1) {
            val y = (mt[kk] and UPPER_MASK) or (mt[kk + 1] and LOWER_MASK)
            mt[kk] = mt[kk + (M - N)] xor (y ushr 1) xor mag01(y)
        }
        val y = (mt[N - 1] and UPPER_MASK) or (mt[0] and LOWER_MASK)
        mt[N - 1] = mt[M - 1] xor (y ushr 1) xor mag01(y)
        index = 0
    }

    private fun mag01(y: Int): Int = if (y and 1 == 0) 0 else MATRIX_A

    private companion object {
        const val N = 624
        const val M = 397
        const val MATRIX_A = 0x9908b0df.toInt()
        const val UPPER_MASK = 0x80000000.toInt()
        const val LOWER_MASK = 0x7fffffff

        /**
         * CPython's int seeding: `abs(n)` as 32-bit little-endian words; zero seeds as a single word.
         * A seed below 2^32 - every seed this project uses - is therefore a one-element key.
         */
        fun seedToKey(seed: Long): IntArray {
            if (seed == 0L) return intArrayOf(0)
            val words = ArrayList<Int>()
            var s = seed
            while (s > 0) {
                words.add((s and 0xFFFFFFFFL).toInt())
                s = s ushr 32
            }
            return words.toIntArray()
        }
    }
}
