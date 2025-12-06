package cn.dataplatform.open.flow.service.core.pack;

import org.redisson.api.RAtomicLong;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/15
 * @since 1.0.0
 */
public abstract class AtomicLongPack {


    /**
     * 增加并获取
     *
     * @param delta 增量
     * @return 返回增加后的值
     */
    public abstract long addAndGet(int delta);

    /**
     * 获取值
     *
     * @return 返回当前值
     */
    public abstract long get();

    /**
     * 设置值
     *
     * @param newValue 新值
     */
    public abstract void set(long newValue);

    public static class Redis extends AtomicLongPack {
        private final RAtomicLong rAtomicLong;

        public Redis(RAtomicLong rAtomicLong) {
            this.rAtomicLong = rAtomicLong;
        }

        @Override
        public long addAndGet(int delta) {
            return this.rAtomicLong.addAndGet(delta);
        }

        @Override
        public long get() {
            return this.rAtomicLong.get();
        }

        @Override
        public void set(long newValue) {
            this.rAtomicLong.set(newValue);
        }
    }

    public static class Jdk extends AtomicLongPack {
        private final AtomicLong atomicLong;

        public Jdk(AtomicLong atomicLong) {
            this.atomicLong = atomicLong;
        }

        @Override
        public long addAndGet(int delta) {
            return this.atomicLong.addAndGet(delta);
        }

        @Override
        public long get() {
            return this.atomicLong.get();
        }

        @Override
        public void set(long newValue) {
            this.atomicLong.set(newValue);
        }

    }

}
