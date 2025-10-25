package org.example.service.blackjack.util;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class Locks {
    private final Object[] stripes = new Object[128];
    public Locks() { for (int i=0;i<stripes.length;i++) stripes[i] = new Object(); }
    public Object of(Long tableId) {
        int idx = Math.abs(Objects.hashCode(tableId)) & (stripes.length - 1);
        return stripes[idx];
    }
}
