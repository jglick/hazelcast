package com.hazelcast.internal.tpc.iouring;

import com.hazelcast.internal.tpc.EventloopTest;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.annotation.QuickTest;
import com.hazelcast.internal.tpc.Eventloop;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class})
public class IOUringEventloopTest extends EventloopTest {

    @Override
    public Eventloop createEventloop() {
        return new IOUringEventloop();
    }

    @Override
    public Eventloop.Type getType() {
        return Eventloop.Type.IOURING;
    }
}
