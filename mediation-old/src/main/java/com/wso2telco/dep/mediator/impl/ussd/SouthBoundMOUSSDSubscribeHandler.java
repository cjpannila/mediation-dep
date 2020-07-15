/*
 *
 *  Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */
package com.wso2telco.dep.mediator.impl.ussd;

import com.wso2telco.dep.mediator.OperatorEndpoint;
import com.wso2telco.dep.mediator.internal.ApiUtils;
import com.wso2telco.dep.mediator.mediationrule.OriginatingCountryCalculatorIDD;
import com.wso2telco.dep.mediator.service.USSDService;
import com.wso2telco.dep.mediator.util.ConfigFileReader;
import com.wso2telco.dep.mediator.util.HandlerUtils;
import com.wso2telco.dep.oneapivalidation.service.IServiceValidate;
import com.wso2telco.dep.oneapivalidation.service.impl.ussd.ValidateUssdSubscription;
import com.wso2telco.dep.subscriptionvalidator.util.ValidatorUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.json.JSONObject;

import java.util.List;

// TODO: Auto-generated Javadoc

/**
 * The Class SouthBoundMOUSSDSubscribeHandler.
 */

public class SouthBoundMOUSSDSubscribeHandler implements USSDHandler {

	/** The log. */
	private Log log = LogFactory.getLog(SouthBoundMOUSSDSubscribeHandler.class);

	/** The Constant API_TYPE. */
	private static final String API_TYPE = "ussd";

	/** The occi. */
	private OriginatingCountryCalculatorIDD occi;

	/** The executor. */
	private USSDExecutor executor;

	/** The ussdDAO. */
	private USSDService ussdService;
	
	private ApiUtils apiUtils;

	/**
	 * Instantiates a new MOUSSD subscribe handler.
	 *
	 * @param ussdExecutor
	 *            the ussd executor
	 */
	public SouthBoundMOUSSDSubscribeHandler(USSDExecutor ussdExecutor) {

		occi = new OriginatingCountryCalculatorIDD();
		this.executor = ussdExecutor;
		ussdService = new USSDService();
		apiUtils = new ApiUtils();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.wso2telco.mediator.impl.ussd.USSDHandler#handle(org.apache.synapse.
	 * MessageContext)
	 */
	@Override
	public boolean handle(MessageContext context) throws Exception {


        JSONObject jsonBody = executor.getJsonBody();
        String notifyUrl = jsonBody.getJSONObject("subscription").getJSONObject("callbackReference").getString("notifyURL");

        String consumerKey = (String) context.getProperty("CONSUMER_KEY");
        String userId = (String) context.getProperty("USER_ID");
        String operatorId="";
        Integer subscriptionId = ussdService.ussdRequestEntry(notifyUrl,consumerKey,operatorId,userId);

        String subsEndpoint = ConfigFileReader.getInstance().getMediatorConfigMap().get("ussdGatewayEndpoint") + subscriptionId;
        log.info("Subsendpoint - " + subsEndpoint);

        List<OperatorEndpoint> endpoints = occi.getAPIEndpointsByApp(API_TYPE, executor.getSubResourcePath(),
                                                                     executor.getValidoperators(context),context);
        if (endpoints.size() > 1) {
            log.warn("Multiple operator endpoints found. Picking first endpoint: " + endpoints.get(0).getEndpointref()
                    .getAddress() + " for operator: " + endpoints.get(0).getOperator() + " to send request.");
        }
        OperatorEndpoint endpoint = endpoints.get(0);
        ussdService.updateOperatorIdBySubscriptionId(subscriptionId, endpoint.getOperator());

        // set information to the message context, to be used in the sequence
        HandlerUtils.setHandlerProperty(context, this.getClass().getSimpleName());
        HandlerUtils.setEndpointProperty(context, endpoint.getEndpointref().getAddress());
        HandlerUtils.setGatewayHost(context);
        HandlerUtils.setAuthorizationHeader(context, executor, endpoint);
        context.setProperty("subsEndPoint", subsEndpoint);
        context.setProperty("operator", endpoint.getOperator());
        context.setProperty("requestResourceUrl", executor.getResourceUrl());
        context.setProperty("subscriptionID", subscriptionId);
        
		context.setProperty("OPERATOR_NAME", endpoint.getOperator());
		context.setProperty("OPERATOR_ID", endpoint.getOperatorId());
        
        return true;
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.wso2telco.mediator.impl.ussd.USSDHandler#validate(java.lang.String,
	 * java.lang.String, org.json.JSONObject, org.apache.synapse.MessageContext)
	 */
	@Override
	public boolean validate(String httpMethod, String requestPath, JSONObject jsonBody, MessageContext context) throws Exception {

		IServiceValidate validator;

		validator = new ValidateUssdSubscription();
		validator.validateUrl(requestPath);
		validator.validate(jsonBody.toString());

		return true;
	}
}
