package com.incidentpilot.common.health;

public record DependencyStatus(String status, String detail) {

    public static DependencyStatus up(String detail) {
        return new DependencyStatus("UP", detail);
    }

    public static DependencyStatus down(Exception exception) {
        return new DependencyStatus("DOWN", exception.getClass().getSimpleName());
    }
}
