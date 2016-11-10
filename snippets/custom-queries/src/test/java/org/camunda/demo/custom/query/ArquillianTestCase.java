package org.camunda.demo.custom.query;

import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.io.File;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.hasItem;

@RunWith(Arquillian.class)
public class ArquillianTestCase {

  @Deployment  
  public static WebArchive createDeployment() {
    File[] libs = Maven.resolver()
    	      .offline(false)
    	      .loadPomFromFile("pom.xml")
    	      .importRuntimeAndTestDependencies().resolve().withTransitivity().asFile();
     
    return ShrinkWrap.create(WebArchive.class, "custom-queries.war")            
    		.addAsLibraries(libs)
            .addAsWebResource("test-processes.xml", "WEB-INF/classes/META-INF/processes.xml")
            .addAsWebResource("test-persistence.xml", "WEB-INF/classes/META-INF/persistence.xml")
            .addAsWebResource("jboss-deployment-structure.xml", "WEB-INF/jboss-deployment-structure.xml")
            .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
            
            .addPackages(false, "org.camunda.demo.custom.query")
            
            .addAsResource("oneTaskProcess.bpmn")
            .addAsResource("customTaskMappings.xml")
            .addAsResource("customMyBatisConfiguration.xml");    
  }

  @After
  public void after() {
    customerService.removeAll();
  }

  @Inject
  private RuntimeService runtimeService;

  @Inject
  private TasklistService tasklistService;

  @Inject
  private CustomerService customerService;

  @Test
  public void test() throws Exception {
    // create unique city
    String regionUnderTest = "Berlin." + System.currentTimeMillis();

    Customer customer = new Customer();
    customer.setName("camunda");
    customer.setRegion(regionUnderTest);
    customerService.persist(customer);
    long customerId = customer.getId();
    
    HashMap<String, Object> variables = new HashMap<String, Object>();
    Set<String> stuffSet = new HashSet<String>();
    stuffSet.add("hello");
    stuffSet.add("world");
    variables.put("stuff", stuffSet);
    variables.put("customerId", customerId);
    
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess", variables);
    
    System.out.println("Started process instance id " + processInstance.getId());
    
    assertThat(runtimeService.getActiveActivityIds(processInstance.getId()), hasItem("theTask"));
    
    List<TaskDTO> tasksForRegion = tasklistService.getTasksForRegion("kermit", regionUnderTest);
    assertEquals(1, tasksForRegion.size());
    TaskDTO task = tasksForRegion.get(0);

    assertNotNull(task.getCustomer());
    assertEquals(regionUnderTest, task.getCustomer().getRegion());
    assertEquals("camunda", task.getCustomer().getName());
    assertEquals(customerId, task.getCustomer().getId());

    List<VariableInstanceEntity> taskVariables = task.getVariables();
    assertEquals(2, taskVariables.size());
    Map<String, Object> dataTuples = task.getDataTuples();
    assertTrue(dataTuples.containsKey("stuff"));
    Set<String> values = (Set<String>) dataTuples.get("stuff");
    assertTrue(values.contains("hello"));
    assertTrue(values.contains("world"));
  }
}
