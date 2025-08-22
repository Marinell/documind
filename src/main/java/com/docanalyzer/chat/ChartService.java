package com.docanalyzer.chat;

import jakarta.enterprise.context.ApplicationScoped;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import java.io.File;
import java.io.IOException;
import org.jfree.chart.ChartUtils;

@ApplicationScoped
public class ChartService {

    public JFreeChart createChart(Chart chartData) {
        switch (chartData.getChartType()) {
            case "bar":
                return createBarChart(chartData);
            case "pie":
                return createPieChart(chartData);
            default:
                throw new IllegalArgumentException("Unsupported chart type: " + chartData.getChartType());
        }
    }

    private JFreeChart createBarChart(Chart chartData) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Chart.Dataset ds : chartData.getDatasets()) {
            int size = Math.min(chartData.getLabels().size(), ds.getData().size());
            for (int i = 0; i < size; i++) {
                dataset.addValue(ds.getData().get(i), ds.getLabel(), chartData.getLabels().get(i));
            }
        }

        return ChartFactory.createBarChart(
                chartData.getTitle(),
                "Category",
                "Value",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);
    }

    private JFreeChart createPieChart(Chart chartData) {
        DefaultPieDataset dataset = new DefaultPieDataset();
        if (chartData.getDatasets() != null && !chartData.getDatasets().isEmpty()) {
            Chart.Dataset firstDataset = chartData.getDatasets().get(0);
            if (firstDataset.getData() != null && chartData.getLabels() != null) {
                int size = Math.min(chartData.getLabels().size(), firstDataset.getData().size());
                for (int i = 0; i < size; i++) {
                    dataset.setValue(chartData.getLabels().get(i), firstDataset.getData().get(i));
                }
            }
        }

        return ChartFactory.createPieChart(
                chartData.getTitle(),
                dataset,
                true, true, false);
    }

    public void saveChartAsPng(JFreeChart chart, String filePath) throws IOException {
        ChartUtils.saveChartAsPNG(new File(filePath), chart, 800, 600);
    }
}
