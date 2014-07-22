package edu.cmu.ml.praprolog.learn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.cmu.ml.praprolog.graph.AnnotatedGraph;
import edu.cmu.ml.praprolog.graph.Feature;
import edu.cmu.ml.praprolog.learn.tools.PosNegRWExample;
import edu.cmu.ml.praprolog.learn.tools.WeightingScheme;
import edu.cmu.ml.praprolog.prove.DprProver;
import edu.cmu.ml.praprolog.util.Dictionary;
import edu.cmu.ml.praprolog.util.ParamVector;

public class AprSRW<T> extends SRW<PosNegRWExample<T>> {
	public static final double DEFAULT_ALPHA=DprProver.MINALPH_DEFAULT;
	public static final double DEFAULT_EPSILON=DprProver.EPS_DEFAULT;
	public static final double DEFAULT_STAYPROB=DprProver.STAYPROB_DEFAULT;
	
	private double alpha;
	private double epsilon;
	private double stayProb;
//	public ParamVector paramVec;
	
//	Map<T,Double> p;
//	Map<T,Double> r;
//	Map<T,Map<String,Double>> dp;
//	Map<T,Map<String,Double>> dr;
//	PosNegRWExample<T> example;
	public AprSRW() {
		super();
		init(DEFAULT_ALPHA,DEFAULT_EPSILON,DEFAULT_STAYPROB);
	}
	
	public AprSRW(int maxT, double mu, double eta, WeightingScheme wScheme, double delta) {
		super(maxT,mu,eta,wScheme,delta);
		init(DEFAULT_ALPHA,DEFAULT_EPSILON,DEFAULT_STAYPROB);
	}
	
	public AprSRW(int maxT, double mu, double eta, WeightingScheme wScheme, double delta,
			double ialpha, double iepsilon, double istayProb) {
		super(maxT,mu,eta,wScheme,delta);
		this.init(ialpha,iepsilon,istayProb);
	}

	private void init(double ialpha, double iepsilon, double istayProb) {
		//set walk parameters here
		alpha = ialpha;
		epsilon = iepsilon;
		stayProb = istayProb;
	}
	
	@Override
	public Map<String, Double> gradient(ParamVector paramVec, PosNegRWExample<T> example) {
		// startNode maps node->weight
		Map<T,Double> query = example.getQueryVec();
		if (query.size() > 1) throw new UnsupportedOperationException("Can't do multi-node queries");
		T startNode = query.keySet().iterator().next();
		
		// gradient maps feature->gradient with respect to that feature
		Map<String,Double> gradient = null;
		
		// maps storing the probability and remainder weights of the nodes:
		HashMap<T,Double> p = new HashMap<T,Double>();
		HashMap<T,Double> r = new HashMap<T,Double>();
		
		// initializing the above maps:
		for(T node : example.getGraph().getNodes())
		{
			p.put(node, 0.0);
			r.put(node, 0.0);
		}
		
		r.putAll(query);
		
		// maps storing the gradients of p and r for each node:
		HashMap<T,Map<String,Double>> dp = new HashMap<T,Map<String,Double>>();
		HashMap<T,Map<String,Double>> dr = new HashMap<T,Map<String,Double>>();
		
		// initializing the above maps:
		for(T node : example.getGraph().getNodes())
		{
			dp.put(node, new HashMap<String,Double>());
			dr.put(node, new HashMap<String,Double>());
			for(String feature : example.getGraph().getFeatureSet())
			{
				dp.get(node).put(feature, 0.0);
				dr.get(node).put(feature, 0.0);
			}
		}
		
		// initialize paramVec and example:
//		this.paramVec = paramVec;
//		this.example = example;
		
		// APR Algorithm:
		int completeCount = 0;
		while(completeCount < example.getGraph().getNumNodes())
		{
			completeCount = 0;
			for(T node : example.getGraph().getNodes())
			{
				if(r.get(node) / (double) example.getGraph().nearNative(node).size() > epsilon)
					while(r.get(node) / example.getGraph().nearNative(node).size() > epsilon)
						this.push(node, p, r, example.getGraph(), paramVec, dp, dr);
				else
					completeCount++;
			}
		}
		
		//for(T node : dp.keySet())
		//	for(String feature : dp.get(node).keySet())
		//		System.out.println(feature + ": " + dp.get(node).get(feature));
		
		// Returning the gradient of the specified node:
//		T start = null;
//		for(T node : startNode.keySet())
//			if(startNode.get(node) == 1)
//				start = node;
		gradient = dp.get(startNode);
		
		return gradient;
	}
	
	private double dotP(List<Feature> phi, ParamVector paramVec) {
		double dotP = 0;
		for(Feature feature : phi)
			dotP += paramVec.get(feature.featureName);
		return dotP;
	}
	
	/**
	 * Simulates a single lazy random walk step on the input vertex
	 * @param u the vertex to be 'pushed'
	 * @param p
	 * @param r
	 * @param graph
	 * @param paramVec
	 * @param dp
	 * @param dr
	 */
	public void push(T u, HashMap<T,Double> p, HashMap<T,Double> r, AnnotatedGraph<T> graph, ParamVector paramVec,
			HashMap<T,Map<String,Double>> dp, HashMap<T,Map<String,Double>> dr)
	{
		// update p for the pushed node:
		Dictionary.increment(p, u, alpha * r.get(u));
		
		// calculate the sum of the weights (raised to exp) of the edges adjacent to the input node:
		double rowSum = 0;
		for(T v : graph.nearNative(u).keySet())
		{
			rowSum += Math.exp(dotP(graph.phi(u, v), paramVec));
		}
		
		// calculate the gradients of the rowSums (needed for the calculation of the gradient of r):
		Map<String, Double> drowSums = new HashMap<String, Double>();
		for(String feature : graph.getFeatureSet())
		{
			// simultaneously update the dp for the pushed node:
			Dictionary.increment(dp,u,feature,alpha * dr.get(u).get(feature));
			double drowSum = 0;
			for(T v : graph.nearNative(u).keySet())
			{
				//for(Feature f : example.getGraph().phi(v, u))
				//	if(feature.equals(f.featureName))
				//		containsF = true;
				if(Feature.contains(graph.phi(v, u), feature))
				{
					drowSum += Math.exp(dotP(graph.phi(u, v), paramVec));
				}
			}
			drowSums.put(feature, drowSum);
		}
		
		// update dr for the pushed vertex, storing dr temporarily for the calculation of dr for the other vertecies:
		Map<String, Double> prevdr = new HashMap<String, Double>();
		for(String feature : graph.getFeatureSet())
		{
			prevdr.put(feature, dr.get(u).get(feature));
			dr.get(u).put(feature, dr.get(u).get(feature) * (1 - alpha) * stayProb);
		}
		
		// update dr for other vertices:
		for(T v : graph.nearNative(u).keySet())
		{
			for(String feature : graph.getFeatureSet())
			{
//				double dotP = 0;
//				int c = 0;//the gradient of dotP w/ respect to feature
//				for(Feature f : graph.phi(u, v))
//				{
//					dotP += paramVec.get(f.featureName);
//					if(f.featureName.equals(feature))
//						c = 1;
//				}
				//for(Feature f : example.getGraph().phi(v, node))
				//{
				//	dotP += paramVec.get(f.featureName);
				//	if(f.featureName.equals(feature))
				//		c = 1;
				//}
				double dotP = Math.exp(dotP(graph.phi(u, v), paramVec));
				int c = Feature.contains(graph.phi(u, v), feature) ? 1 : 0;
				double aNdr = dr.get(v).get(feature);
				//if(feature.equals("fromr"))
				//	System.out.println(c + " : " + drowSums.get(feature) + " : " + rowSum);
				
				// whoa this is pretty gross.
				aNdr += (1-stayProb)*(1-alpha)*((prevdr.get(feature)*dotP/rowSum)+(r.get(u)*((c*dotP*rowSum)-(dotP*drowSums.get(feature)))/(rowSum*rowSum)));
				dr.get(v).put(feature, aNdr);
			}
		}
		
		// update r for all affected vertices:
		double ru = r.get(u);
		r.put(u, ru * stayProb * (1 - alpha));
		for(T v : graph.nearNative(u).keySet())
		{
			// calculate edge weight on v:
			double dotP = Math.exp(dotP(graph.phi(u, v), paramVec));
			r.put(v, r.get(v) + (1 - stayProb) * (1 - alpha) * (dotP / rowSum) * ru);
		}
	}
}