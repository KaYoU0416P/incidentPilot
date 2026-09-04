package com.incidentpilot.retrieval;

@FunctionalInterface
public interface Retriever {

    RetrievalResult retrieve(RetrievalQuery query);
}
