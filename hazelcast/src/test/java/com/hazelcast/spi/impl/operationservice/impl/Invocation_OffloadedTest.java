package com.hazelcast.spi.impl.operationservice.impl;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.spi.CallStatus;
import com.hazelcast.spi.InternalCompletableFuture;
import com.hazelcast.spi.Offload;
import com.hazelcast.spi.Operation;
import com.hazelcast.spi.properties.GroupProperty;
import com.hazelcast.test.ExpectedRuntimeException;
import com.hazelcast.test.HazelcastParallelClassRunner;
import com.hazelcast.test.HazelcastTestSupport;
import com.hazelcast.test.TestHazelcastInstanceFactory;
import com.hazelcast.test.annotation.ParallelTest;
import com.hazelcast.test.annotation.QuickTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(HazelcastParallelClassRunner.class)
@Category({QuickTest.class, ParallelTest.class})
public class Invocation_OffloadedTest extends HazelcastTestSupport {

    private OperationServiceImpl localOperationService;
    private TestHazelcastInstanceFactory instanceFactory;

    @Before
    public void setup() {
        instanceFactory = createHazelcastInstanceFactory();
        Config config = new Config();
        config.setProperty(GroupProperty.MAX_JOIN_SECONDS.getName(), "5");

        HazelcastInstance[] cluster = instanceFactory.newInstances(config, 1);

        localOperationService = getOperationServiceImpl(cluster[0]);
    }

    @Test(expected = ExpectedRuntimeException.class)
    public void whenStartThrowsException_thenExceptionPropagated() {
        InternalCompletableFuture f = localOperationService.invokeOnPartition(new OffloadingOperation(new OffloadFactory() {
            @Override
            public Offload create(Operation op) {
                return new Offload(op) {
                    @Override
                    public void start() {
                        throw new ExpectedRuntimeException();
                    }
                };
            }
        }));

        assertCompletesEventually(f);
        f.join();
    }

    @Test
    public void whenCompletesInStart() throws Exception {
        final String response = "someresponse";
        OffloadingOperation source = new OffloadingOperation(new OffloadFactory() {
            @Override
            public Offload create(Operation op) {
                return new Offload(op) {
                    @Override
                    public void start() {
                        offloadedOperation().sendResponse("someresponse");
                    }
                };
            }
        });

        InternalCompletableFuture<String> f = localOperationService.invokeOnPartition(source);

        assertCompletesEventually(f);
        assertEquals(response, f.get());
        // make sure the source operation isn't registered anymore
        assertFalse(localOperationService.asyncOperations.contains(source));
    }

    @Test
    public void whenCompletesEventually() throws Exception {
        final String response = "someresponse";

        InternalCompletableFuture<String> f = localOperationService.invokeOnPartition(new OffloadingOperation(new OffloadFactory() {
            @Override
            public Offload create(Operation op) {
                return new Offload(op) {
                    @Override
                    public void start() {
                        new Thread() {
                            @Override
                            public void run() {
                                sleepSeconds(5);
                                offloadedOperation().sendResponse(response);
                            }
                        }.start();
                    }
                };
            }
        }));

        assertCompletesEventually(f);
        assertEquals(response, f.get());
    }

    @Test
    public void whenOffloaded_thenAsyncOperationRegisteredOnStart_andUnregisteredOnCompletion() {
        OffloadingOperation source = new OffloadingOperation(new OffloadFactory() {
            @Override
            public Offload create(Operation op) {
                return new Offload(op) {
                    @Override
                    public void start() {
                        // we make sure that the operation is registered
                        assertTrue(localOperationService.asyncOperations.contains(offloadedOperation()));
                        offloadedOperation().sendResponse("someresponse");
                    }
                };
            }
        });

        InternalCompletableFuture<String> f = localOperationService.invokeOnPartition(source);

        assertCompletesEventually(f);
        // make sure the source operation isn't registered anymore
        assertFalse(localOperationService.asyncOperations.contains(source));
    }

    private interface OffloadFactory {
        Offload create(Operation op);
    }

    public static class OffloadingOperation extends Operation {
        private final OffloadFactory offloadFactory;

        public OffloadingOperation(OffloadFactory offloadFactory) {
            this.offloadFactory = offloadFactory;
            setPartitionId(0);
        }

        @Override
        public CallStatus call() throws Exception {
            return offloadFactory.create(this);
        }
    }
}

