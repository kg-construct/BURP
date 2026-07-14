package burp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ViewJoin {

    public LogicalView parentLogicalView;
    public List<JoinCondition> joinConditions = new ArrayList<>();
    public List<ExpressionField> expressionFields = new ArrayList<>();
    public boolean isInnerJoin = false;

    private List<LogicalIteration> iterations = null;

    public List<LogicalIteration> expand(List<LogicalIteration> childIterations) {
        try {
            if (iterations == null) {
                // Get logical iterations of parent logical view
                iterations = StreamSupport
                        .stream(Spliterators.spliteratorUnknownSize(parentLogicalView.iterator(), 0), false)
                        .map(a -> (LogicalIteration) a)
                        .collect(Collectors.toList());
            }

            List<LogicalIteration> newList = new ArrayList<>();

            for (LogicalIteration childIteration : childIterations) {
                boolean hasACorrespondence = false;

                // Discussion with Els. For each child, the counter for the parents are "reset" to 0
                // So we assign 0, 1, ..., n from the child's perspective.
                int count = -1; // start at -1 as no match found yet

                for(LogicalIteration parentIteration : iterations) {
                    if(matches(childIteration, parentIteration)) {
                        hasACorrespondence = true;

                        // Increment the index
                        count++;

                        // Create new logical iterations for each value of each expression map
                        List<LogicalIteration> result = new ArrayList<>();
                        result.add(childIteration);
                        for (ExpressionField e : expressionFields) {
                            result = enrichForJoin(e, count, result, parentIteration);
                        }

                        // add all new iterations to the list
                        newList.addAll(result);
                    }
                }

                // Make the outer join if there is no match
                if(!hasACorrespondence && !isInnerJoin) {
                    LogicalIteration newIteration = childIteration.copy();
                    for (ExpressionField e : expressionFields) {
                        newIteration.put(e.fieldName, null);
                        newIteration.put(e.fieldName + ".#", null);
                    }
                    newList.add(newIteration);
                }
            }

            return newList;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private List<LogicalIteration> enrichForJoin(ExpressionField e, int index, List<LogicalIteration> result, LogicalIteration parentIteration) {
        List<LogicalIteration> nlist = new ArrayList<>();

        for(LogicalIteration li : result) {
            for (Object o : e.fieldExpressionMap.generateValues(parentIteration)) {
                LogicalIteration newLogicalIteration = li.copy();
                newLogicalIteration.put(e.fieldName, o);
                newLogicalIteration.put(e.fieldName + ".#", index);
                nlist.add(newLogicalIteration);
            }
        }

        return nlist;
    }

    private boolean matches(LogicalIteration childIteration, LogicalIteration parentIteration) {
        // Expression Maps are multi-valued. We thus need
        // For each join condition at least one match.
        boolean ok = true;
        for (JoinCondition jc : joinConditions) {

            List<Object> values1 = jc.childMap.generateValues(childIteration);
            List<Object> values2 = jc.parentMap.generateValues(parentIteration);

            if (values1.stream().distinct().filter(values2::contains)
                    .collect(Collectors.toSet()).isEmpty()) {
                // No match, break.
                ok = false;
                break;
            }
        }
        return ok;
    }

    public List<ExpressionField> getExpressionFields() {
        return expressionFields;
    }

    public void addField(Field field) {
        field.parent = parentLogicalView;
        if (field instanceof ExpressionField) {
            getExpressionFields().add((ExpressionField) field);
        }
        else
            throw new RuntimeException("Unknown field type for ViewJoin.");
    }

}
