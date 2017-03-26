/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.operators;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Observable.Operator;
import rx.Producer;
import rx.Subscriber;
import rx.internal.producers.ProducerArbiter;
import rx.observers.SerializedSubscriber;
import rx.subscriptions.SerialSubscription;

/**
 * Transforms an Observable that emits Observables into a single Observable that
 * emits the items emitted by the most recently published of those Observables.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/switchDo.png" alt="">
 * 
 * @param <T> the value type
 */
public final class OperatorSwitch<T> implements Operator<T, Observable<? extends T>> {
    /** Lazy initialization via inner-class holder. */
    private static final class Holder {
        /** A singleton instance. */
        static final OperatorSwitch<Object> INSTANCE = new OperatorSwitch<Object>();
    }
    /**
     * @return a singleton instance of this stateless operator.
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> OperatorSwitch<T> instance() {
        return (OperatorSwitch<T>)Holder.INSTANCE;
    }
    
    private OperatorSwitch() { }
    
    @Override
    public Subscriber<? super Observable<? extends T>> call(final Subscriber<? super T> child) {
        SwitchSubscriber<T> sws = new SwitchSubscriber<T>(child);
        child.add(sws);
        return sws;
    }

    private static final class SwitchSubscriber<T> extends Subscriber<Observable<? extends T>> {
        final SerializedSubscriber<T> serializedChild;
        final SerialSubscription ssub;
        final Object guard = new Object();
        final NotificationLite<?> nl = NotificationLite.instance();
        final ProducerArbiter arbiter;
        
        /** Guarded by guard. */
        int index;
        /** Guarded by guard. */
        boolean active;
        /** Guarded by guard. */
        boolean mainDone;
        /** Guarded by guard. */
        List<Object> queue;
        /** Guarded by guard. */
        boolean emitting;
        /** Guarded by guard. */
        InnerSubscriber<T> currentSubscriber;

        SwitchSubscriber(Subscriber<? super T> child) {
            serializedChild = new SerializedSubscriber<T>(child);
            arbiter = new ProducerArbiter();
            ssub = new SerialSubscription();
            child.add(ssub);
            child.setProducer(new Producer(){

                @Override
                public void request(long n) {
                    if (n > 0) {
                        arbiter.request(n);
                    }
                }
            });
        }

        @Override
        public void onNext(Observable<? extends T> t) {
            final int id;
            synchronized (guard) {
                id = ++index;
                active = true;
                currentSubscriber = new InnerSubscriber<T>(id, arbiter, this);
            }
            ssub.set(currentSubscriber);
            t.unsafeSubscribe(currentSubscriber);
        }

        @Override
        public void onError(Throwable e) {
            serializedChild.onError(e);
            unsubscribe();
        }

        @Override
        public void onCompleted() {
            List<Object> localQueue;
            synchronized (guard) {
                mainDone = true;
                if (active) {
                    return;
                }
                if (emitting) {
                    if (queue == null) {
                        queue = new ArrayList<Object>();
                    }
                    queue.add(nl.completed());
                    return;
                }
                localQueue = queue;
                queue = null;
                emitting = true;
            }
            drain(localQueue);
            serializedChild.onCompleted();
            unsubscribe();
        }
        void emit(T value, int id, InnerSubscriber<T> innerSubscriber) {
            List<Object> localQueue;
            synchronized (guard) {
                if (id != index) {
                    return;
                }
                if (emitting) {
                    if (queue == null) {
                        queue = new ArrayList<Object>();
                    }
                    queue.add(value);
                    return;
                }
                localQueue = queue;
                queue = null;
                emitting = true;
            }
            boolean once = true;
            boolean skipFinal = false;
            try {
                do {
                    drain(localQueue);
                    if (once) {
                        once = false;
                        serializedChild.onNext(value);
                        arbiter.produced(1);                        
                    }
                    synchronized (guard) {
                        localQueue = queue;
                        queue = null;
                        if (localQueue == null) {
                            emitting = false;
                            skipFinal = true;
                            break;
                        }
                    }
                } while (!serializedChild.isUnsubscribed());
            } finally {
                if (!skipFinal) {
                    synchronized (guard) {
                        emitting = false;
                    }
                }
            }
        }
        void drain(List<Object> localQueue) {
            if (localQueue == null) {
                return;
            }
            for (Object o : localQueue) {
                if (nl.isCompleted(o)) {
                    serializedChild.onCompleted();
                    break;
                } else
                if (nl.isError(o)) {
                    serializedChild.onError(nl.getError(o));
                    break;
                } else {
                    @SuppressWarnings("unchecked")
                    T t = (T)o;
                    serializedChild.onNext(t);
                    arbiter.produced(1);
                }
            }
        }

        void error(Throwable e, int id) {
            List<Object> localQueue;
            synchronized (guard) {
                if (id != index) {
                    return;
                }
                if (emitting) {
                    if (queue == null) {
                        queue = new ArrayList<Object>();
                    }
                    queue.add(nl.error(e));
                    return;
                }

                localQueue = queue;
                queue = null;
                emitting = true;
            }

            drain(localQueue);
            serializedChild.onError(e);
            unsubscribe();
        }
        void complete(int id) {
            List<Object> localQueue;
            synchronized (guard) {
                if (id != index) {
                    return;
                }
                active = false;
                if (!mainDone) {
                    return;
                }
                if (emitting) {
                    if (queue == null) {
                        queue = new ArrayList<Object>();
                    }
                    queue.add(nl.completed());
                    return;
                }

                localQueue = queue;
                queue = null;
                emitting = true;
            }

            drain(localQueue);
            serializedChild.onCompleted();
            unsubscribe();
        }

    }
    
    private static final class InnerSubscriber<T> extends Subscriber<T> {

        private final int id;

        private final ProducerArbiter arbiter;

        private final SwitchSubscriber<T> parent;

        InnerSubscriber(int id, ProducerArbiter arbiter, SwitchSubscriber<T> parent) {
            this.id = id;
            this.arbiter = arbiter;
            this.parent = parent;
        }
        
        @Override
        public void setProducer(Producer p) {
            arbiter.setProducer(p);
        }

        @Override
        public void onNext(T t) {
            parent.emit(t, id, this);
        }

        @Override
        public void onError(Throwable e) {
            parent.error(e, id);
        }

        @Override
        public void onCompleted() {
            parent.complete(id);
        }
    }

}
