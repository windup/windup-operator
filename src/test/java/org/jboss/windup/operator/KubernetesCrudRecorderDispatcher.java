package org.jboss.windup.operator;

import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudAttributesExtractor;
import io.fabric8.kubernetes.client.server.mock.KubernetesCrudDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesResponseComposer;
import io.fabric8.mockwebserver.crud.ResponseComposer;
import lombok.Setter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Setter
/*
This class will allow to check the requests made to the Kubernetes API
in order to do the asserts
*/
public class KubernetesCrudRecorderDispatcher extends KubernetesCrudDispatcher {
  private List<Request> requests = new ArrayList<>();

  public KubernetesCrudRecorderDispatcher() {
      this(Collections.emptyList());
  }

  public KubernetesCrudRecorderDispatcher(List<CustomResourceDefinitionContext> crdContexts) {
    this(new KubernetesCrudAttributesExtractor(crdContexts), new KubernetesResponseComposer());
  }

  public KubernetesCrudRecorderDispatcher(KubernetesCrudAttributesExtractor attributeExtractor, ResponseComposer responseComposer) {
    super(attributeExtractor, responseComposer);
  }

  @Override
  public MockResponse dispatch(RecordedRequest request) {
      requests.add(new Request(request.getPath(), request.getMethod(), request.getBody().clone().readUtf8()));
      return super.dispatch(request);
  }

    // to avoid the ConcurrentModificationException that happens when reading the list inside a Stream but also adding elements to it
  public List<Request> getRequests() {
      return new ArrayList<Request>(requests);
  }

}
