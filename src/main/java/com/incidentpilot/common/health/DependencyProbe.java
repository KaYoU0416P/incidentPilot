package com.incidentpilot.common.health;

interface DependencyProbe {

    String name();

    DependencyStatus check();
}
