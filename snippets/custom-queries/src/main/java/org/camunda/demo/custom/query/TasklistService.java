package org.camunda.demo.custom.query;

import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;
import org.camunda.bpm.engine.variable.value.TypedValue;

import javax.ejb.Stateless;
import javax.inject.Named;
import javax.sql.DataSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Properties;

@Stateless
@Named
public class TasklistService {

	public Properties getSqlSessionFactoryProperties(ProcessEngineConfigurationImpl conf) {
		Properties properties = new Properties();		
		ProcessEngineConfigurationImpl.initSqlSessionFactoryProperties(properties, conf.getDatabaseTablePrefix(), conf.getDatabaseType());
		return properties;
	}

	public SqlSessionFactory createMyBatisSqlSessionFactory() {
		InputStream config = this.getClass().getResourceAsStream("/customMybatisConfiguration.xml");
		ProcessEngine processEngine = ProcessEngines.getDefaultProcessEngine();
		ProcessEngineConfiguration processEngineConfiguration = processEngine.getProcessEngineConfiguration();
		DataSource dataSource = processEngineConfiguration.getDataSource();

		// use this transaction factory if you work in a non transactional
		// environment
		// TransactionFactory transactionFactory = new JdbcTransactionFactory();

		// use this transaction factory if you work in a transactional
		// environment (e.g. called within the engine or using JTA)
		TransactionFactory transactionFactory = new ManagedTransactionFactory();

		Environment environment = new Environment("customTasks", transactionFactory, dataSource);

		XMLConfigBuilder parser = new XMLConfigBuilder( //
				new InputStreamReader(config), //
				"", // set environment later via code
				getSqlSessionFactoryProperties((ProcessEngineConfigurationImpl) processEngineConfiguration));
		Configuration configuration = parser.getConfiguration();
		configuration.setEnvironment(environment);
		configuration = parser.parse();

		configuration.setDefaultStatementTimeout(processEngineConfiguration.getJdbcStatementTimeout());

		SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
		return sqlSessionFactory;
	}

	public List<TaskDTO> getTasksForRegion(final String assignee, final String region) {
		//in production code, factory will be injected via spring
		SqlSessionFactory sqlSessionFactory = createMyBatisSqlSessionFactory();
		// compare to http://www.mybatis.org/mybatis-3/getting-started.html

		SqlSession session = sqlSessionFactory.openSession();
		try {
			// You can use this object to leverage Camunda Pagination features
			ListQueryParameterObject queryParameterObject = new ListQueryParameterObject();
			queryParameterObject.setParameter(region);

			List<TaskDTO> tasks = session.selectList("customTask.selectTasksForRegion", queryParameterObject);
			TaskDTO taskDTO = tasks.get(0);
			List<VariableInstanceEntity> taskVariables = taskDTO.getVariables();
			for(int i =0 ; i< taskVariables.size(); i++) {
				VariableInstanceEntity variableInstanceEntity = taskVariables.get(i);
				String name = variableInstanceEntity.getName();
				TypedValue typedValue = variableInstanceEntity.getTypedValue(true);
				taskDTO.addDataTuple(name, typedValue.getValue());
			}
			return tasks;
		} finally {
			session.close();
		}
	}

}
