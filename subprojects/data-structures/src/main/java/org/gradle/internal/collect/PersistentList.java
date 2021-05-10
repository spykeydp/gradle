/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.collect;

import org.gradle.internal.Cast;

import javax.annotation.CheckReturnValue;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class PersistentList<T> {
    public static <T> PersistentList<T> of() {
        return Cast.uncheckedNonnullCast(NIL);
    }

    public static <T> PersistentList<T> of(T first) {
        return PersistentList.<T>of().plus(first);
    }


    public abstract void forEach(Consumer<? super T> consumer);

    @CheckReturnValue
    public abstract PersistentList<T> plus(T element);

    private PersistentList() {}

    private static final PersistentList<Object> NIL = new PersistentList<Object>() {
        @Override
        public void forEach(Consumer<? super Object> consumer) {
        }

        @Override
        public PersistentList<Object> plus(Object element) {
            return new Cons<>(element, this);
        }

        @Override
        public String toString() {
            return "Nil";
        }
    };

    private static class Cons<T> extends PersistentList<T> {
        private final T head;
        private final PersistentList<T> tail;

        public Cons(T head, PersistentList<T> tail) {
            this.head = head;
            this.tail = tail;
        }

        @Override
        public void forEach(Consumer<? super T> consumer) {
            consumer.accept(head);
            tail.forEach(consumer);
        }

        @Override
        public PersistentList<T> plus(T element) {
            return new Cons<>(element, this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Cons<?> cons = (Cons<?>) o;
            return head.equals(cons.head) && tail.equals(cons.tail);
        }

        @Override
        public int hashCode() {
            return Objects.hash(head, tail);
        }

        @Override
        public String toString() {
            return head + " : " + tail;
        }
    }
}
