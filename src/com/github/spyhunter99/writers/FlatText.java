package com.github.spyhunter99.writers;

import com.github.spyhunter99.model.CountData;
import com.github.spyhunter99.model.Metric;
import com.github.spyhunter99.model.Node;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by alex on 10/7/16.
 */
public class FlatText  implements IWriter{
    @Override
    public String getReport(CountData data) {
        StringBuilder sb = new StringBuilder();

            CountData countData = data;
            sb.append("Processing ").
                    append(countData.fileName).
                    append("\nRead in ").
                    append(countData.overallMetrics.methodCount).
                    append(" method IDs and ").
                    append(countData.overallMetrics.fieldCount).
                    append(" field IDs\n");
            sb.append("----------------------------------\n");
            sb.append("Package details:\n");

            Node ptr = countData.packageTree;
            Iterator<Map.Entry<String, Node>> iterator = ptr.children.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<String, Node> next = iterator.next();
                print (next.getValue(), next.getKey(), "",sb);
            }
        return sb.toString();
    }

    public String getReport(List<CountData> data){
        StringBuilder sb = new StringBuilder();
        for (int i=0; i < data.size(); i++){
            sb.append(getReport(data.get(i)));
        }
        return sb.toString();
    }

    private void print(Node ptr, String pgk, String indent, StringBuilder sb) {
        if (ptr!=null){
            Metric count = ptr.count;
            sb.append(indent).append(pgk).append(" Methods ").
                    append(count.methodCount).
                    append(" Fields ").
                    append(count.fieldCount).
                    append("\n");
            String indent2=indent + "\t";
            Iterator<Map.Entry<String, Node>> iterator = ptr.children.entrySet().iterator();
            while (iterator.hasNext()){
                Map.Entry<String, Node> next = iterator.next();
                print (next.getValue(), next.getKey(), indent2,sb);
            }

        }
    }
}
