package org.seal.xacml.mutation;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.seal.policyUtils.PolicyLoader;
import org.seal.semanticMutation.Mutant;
import org.seal.semanticMutation.Mutator;
import org.seal.xacml.RequestGeneratorBase;
import org.seal.xacml.TaggedRequest;
import org.seal.xacml.utils.FileIOUtil;
import org.seal.xacml.utils.RequestBuilder;
import org.seal.xacml.utils.XACMLElementUtil;
import org.seal.xacml.utils.Z3StrUtil;
import org.wso2.balana.AbstractPolicy;
import org.wso2.balana.ParsingException;
import org.wso2.balana.xacml3.AnyOfSelection;
import org.wso2.balana.xacml3.Target;
import org.xml.sax.SAXException;

public class MutationBasedTestGenerator extends RequestGeneratorBase {
	private AbstractPolicy policy;
	public MutationBasedTestGenerator(String policyFilePath) throws ParsingException, IOException, SAXException, ParserConfigurationException{
		init(policyFilePath);
		this.policy = PolicyLoader.loadPolicy(new File(policyFilePath));
	}	   
	
	public List<TaggedRequest> generateRequests(List<String> mutationMethods) throws IOException, ParserConfigurationException, ParsingException, SAXException, InvocationTargetException, IllegalAccessException, NoSuchMethodException{
		Mutator mutator = new Mutator(new Mutant(policy, XACMLElementUtil.getPolicyName(policyFilePath)));
        Map<String,List<Mutant>> mutantsMap = mutator.generateMutantsCategorizedByMethods(mutationMethods);
        Class<? extends MutationBasedTestGenerator> cls = this.getClass();
        Class[] noParams = {};
        List<TaggedRequest> taggedRequests = new ArrayList<TaggedRequest>();
		for(Map.Entry<String, List<Mutant>> e:mutantsMap.entrySet()){
			List<Mutant> mutants = (List<Mutant>)e.getValue();
			String tag = MutationMethodAbbrDirectory.getAbbr(e.getKey().toString());
			String methodName = "generate" + tag + "Requests";
			Method method = cls.getDeclaredMethod(methodName, noParams);
			List<String> requests = (List<String>)method.invoke(this, null);
			for(String request:requests){
				File r = new File(tag);
				FileIOUtil.writeFile(r, request);
				
				for(Mutant mutant:mutants){
					File f = new File(mutant.getName());
					FileIOUtil.writeFile(f, mutant.encode());
					if(doRequestPropagatesMutationFault(request, policy, mutant)){
						taggedRequests.add(new TaggedRequest(tag,request));
					}
				}
			}
		}
		return taggedRequests;
	}

	private boolean doRequestPropagatesMutationFault(String request, AbstractPolicy policy, Mutant mutant) throws ParsingException{
		String req = request.replaceAll(System.lineSeparator(), " ").trim(); 
		if(req.isEmpty()){
			return false;
		}
		AbstractPolicy mutantPolicy = mutant.getPolicy();
		int pRes = XACMLElementUtil.evaluateRequestForPolicy(policy, req);
		int mRes = XACMLElementUtil.evaluateRequestForPolicy(mutantPolicy, req);
		if (pRes == mRes){
			return false;
		} else {
			return true;
		}
	}
	
	public List<String> generatePTTRequests() throws IOException{
		if(!policy.isTargetEmpty()){
			Target policyTarget = (Target)policy.getTarget();
			List<AnyOfSelection> anyOf = policyTarget.getAnyOfSelections();
			if(anyOf.size() != 0){
				String expression = z3ExpressionHelper.getFalseTargetExpression(policyTarget).toString();
				boolean sat = Z3StrUtil.processExpression(expression, z3ExpressionHelper);
				String request = RequestBuilder.buildRequest(z3ExpressionHelper.getAttributeList());
				List<String> requests = new ArrayList<String>();
				requests.add(request);
				if (sat == true) {
				    setRequests(requests);
				} else{
					setRequests(null);
				}
			}
		}
		return getRequests();
	}
	
	public List<String> generatePTFRequests() throws IOException{
		if(!policy.isTargetEmpty()){
			Target policyTarget = (Target)policy.getTarget();
			List<AnyOfSelection> anyOf = policyTarget.getAnyOfSelections();
			if(anyOf.size() != 0){
				String expression = z3ExpressionHelper.getTrueTargetExpression(policyTarget).toString();
				boolean sat = Z3StrUtil.processExpression(expression, z3ExpressionHelper);
				String request = RequestBuilder.buildRequest(z3ExpressionHelper.getAttributeList());
				List<String> requests = new ArrayList<String>();
				requests.add(request);
				if (sat == true) {
				    setRequests(requests);
				} else{
					setRequests(null);
				}
			}
		}
		return getRequests();
	}
}
