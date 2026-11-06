package com.metrocitypower.portal.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Named counter row (e.g. "APP-2026" -> 17). Read with a pessimistic lock so concurrent submits never share an id. */
@Entity
@Table(name = "id_counters")
public class IdCounterEntity {

    @Id
    @Column(length = 32)
    private String name;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected IdCounterEntity() {
    }

    public IdCounterEntity(String name, long lastValue) {
        this.name = name;
        this.lastValue = lastValue;
    }

    public long next() {
        return ++lastValue;
    }

    public String getName() { return name; }
    public long getLastValue() { return lastValue; }
}
