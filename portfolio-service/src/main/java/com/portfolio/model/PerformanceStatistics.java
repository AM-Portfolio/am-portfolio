package com.portfolio.model;

/**
 * Model class to hold performance statistics data
 */
public class PerformanceStatistics {
    private final double averagePerformance;
    private final double medianPerformance;
    private final double bestPerformance;
    private final double worstPerformance;
    
    public PerformanceStatistics(
            double averagePerformance, 
            double medianPerformance, 
            double bestPerformance, 
            double worstPerformance) {
        this.averagePerformance = averagePerformance;
        this.medianPerformance = medianPerformance;
        this.bestPerformance = bestPerformance;
        this.worstPerformance = worstPerformance;
    }
    
    public double getAveragePerformance() { return averagePerformance; }
    public double getMedianPerformance() { return medianPerformance; }
    public double getBestPerformance() { return bestPerformance; }
    public double getWorstPerformance() { return worstPerformance; }
    
    /**
     * Factory method to create statistics from an array of performance percentages
     * 
     * @param percentages Array of performance percentages
     * @param isGainers Whether we're looking at gainers (true) or losers (false)
     * @return A new PerformanceStatistics object
     */
    public static PerformanceStatistics fromPercentages(double[] percentages, boolean isGainers) {
        if (percentages == null || percentages.length == 0) {
            return new PerformanceStatistics(0, 0, 0, 0);
        }
        
        double average = calculateAverage(percentages);
        double median = calculateMedian(percentages);
        double best = isGainers ? percentages[0] : percentages[percentages.length - 1];
        double worst = isGainers ? percentages[percentages.length - 1] : percentages[0];
        
        return new PerformanceStatistics(average, median, best, worst);
    }
    
    /**
     * Calculates the average of an array of values
     * 
     * @param values Array of values
     * @return The average value
     */
    private static double calculateAverage(double[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }
    
    /**
     * Calculates the median of an array of values
     * 
     * @param values Array of values (assumed to be sorted)
     * @return The median value
     */
    private static double calculateMedian(double[] values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        
        int middle = values.length / 2;
        if (values.length % 2 == 1) {
            return values[middle];
        } else {
            return (values[middle-1] + values[middle]) / 2.0;
        }
    }
}