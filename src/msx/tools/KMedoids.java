/*
 * Santiago Ontanon Villar.
 */
package msx.tools;

/**
 *
 * @author santi
 */
public class KMedoids {
    public static boolean DEBUG = false;
    
    
    public static int[] kMedoids(double m[][], double weights[], int k)
    {
        int n = m.length;
        int medoids[] = new int[k];
        boolean ismedoid[] = new boolean[n];
        int association[] = new int[n];
        int newassociation[] = new int[n];
        
        // initial medoid assignment:
        for(int i = 0;i<n;i++) ismedoid[i] = false;
        for(int i = 0;i<k;i++) {
            medoids[i] = i;
            ismedoid[i] = true;
        }
        
        // initial assignment:
        double cost = kMedoidsCost(m, medoids, association, weights);
        if (DEBUG) System.out.println("Initial assignment cost: " + cost);
        
        // iterate:
        boolean improvement;
        do{
            if (DEBUG) System.out.println("iteration...");
            improvement = false;
            for(int medoid = 0;medoid<k;medoid++) {
                for(int i = 0;i<n;i++) {
                    if (ismedoid[i]) continue;
                    
                    // try to swap the medoid:
                    int oldMedoid = medoids[medoid];
                    medoids[medoid] = i;
                    
//                    double newCost = kMedoidsCost(m, medoids, association);
                    for(int j = 0;j<n;j++) newassociation[j] = association[j];
                    double newCost = kMedoidsCostAfterSwap(m, medoids, newassociation, medoid, weights);
                    if (newCost < cost) {
                        cost = newCost;
                        improvement = true;
                        if (DEBUG) System.out.println("Better assignment cost: " + cost + " (medoid["+medoid+"] = " + i + ")");
                        ismedoid[oldMedoid] = false;
                        ismedoid[i] = true;
                        for(int j = 0;j<n;j++) association[j] = newassociation[j];
                    } else {
                        medoids[medoid] = oldMedoid;
                    }
                }
            }
        }while(improvement);
        
        return medoids;
    }
    
    
    public static double kMedoidsCost(double m[][], int medoids[], int association[], double weights[])
    {
        int n = m.length;
        int k = medoids.length;
        double cost = 0;
        int association_tmp;
        double association_d;
        for(int i = 0;i<n;i++) {
            association_tmp = -1;
            association_d = 0;
            for(int j = 0;j<k;j++) {
                if (association_tmp == -1 ||
                    m[i][medoids[j]] < association_d) {
                    association_tmp = j;
                    association_d = m[i][medoids[j]];
                }
            }
            association[i] = association_tmp;
            cost += m[i][medoids[association[i]]]*weights[i];
        }
        return cost;
    }    


    public static double kMedoidsCostAfterSwap(double m[][], int medoids[], int association[], int medoidChanged, double weights[])
    {
        int n = m.length;
        int k = medoids.length;
        double cost = 0;
        int association_tmp;
        double association_d;
        for(int i = 0;i<n;i++) {
            if (association[i] == medoidChanged) {
                association_tmp = -1;
                association_d = 0;
                for(int j = 0;j<k;j++) {
                    if (association_tmp == -1 ||
                        m[i][medoids[j]]*weights[i] < association_d) {
                        association_tmp = j;
                        association_d = m[i][medoids[j]]*weights[i];
                    }
                }
                association[i] = association_tmp;
                cost += m[i][medoids[association[i]]]*weights[i];
            } else {
                if (m[i][medoids[medoidChanged]]*weights[i] < m[i][medoids[association[i]]]*weights[i]) {
                    association[i] = medoidChanged;
                }
                cost += m[i][medoids[association[i]]]*weights[i];
            }
        }
        return cost;
    }    
}
