package org.jasome.metrics.calculators;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.graph.Graph;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jasome.metrics.Calculator;
import org.jasome.metrics.Metric;
import org.jasome.input.Method;
import org.jasome.input.Package;
import org.jasome.input.Type;
import org.jasome.util.CalculationUtils;
import org.jscience.mathematics.number.LargeInteger;
import org.jscience.mathematics.number.Rational;

import java.util.*;
import java.util.stream.Collectors;

/**
 * http://support.objecteering.com/objecteering6.1/help/us/metrics/metrics_in_detail/specialization_index.htm
 */
public class SpecializationIndexCalculator implements Calculator<Type> {


    @Override
    public Set<Metric> calculate(Type type) {


        LargeInteger depth = LargeInteger.valueOf(calculateInheritanceDepth(type));

        Pair<Integer, Integer> overloadedAndInheritedOperations = calculateOverloadedAndInheritedOperations(type);

        LargeInteger overriddenMethods = LargeInteger.valueOf(overloadedAndInheritedOperations.getLeft());
        LargeInteger inheritedMethods = LargeInteger.valueOf(overloadedAndInheritedOperations.getRight());

        LargeInteger numberOfMethods = LargeInteger.valueOf(type.getMethods().size());

        //more good and related ones here http://www.cs.kent.edu/~jmaletic/cs63901/lectures/SoftwareMetrics.pdf

        ImmutableSet.Builder<Metric> metricBuilder = ImmutableSet.<Metric>builder()
                .add(Metric.of("DIT", "Depth of Inheritance Tree", depth))
                .add(Metric.of("NORM", "Number of Overridden Methods", overriddenMethods))
                .add(Metric.of("NM", "Number of Methods", numberOfMethods))
                .add(Metric.of("NMI", "Number of Inherited Methods", inheritedMethods))
                .add(Metric.of("NMA", "Number of Methods Added to Inheritance", numberOfMethods.minus(overriddenMethods)));

        if (numberOfMethods.isGreaterThan(LargeInteger.ZERO)) {
            LargeInteger numerator = overriddenMethods.times(depth);
            Rational specializationIndex = Rational.valueOf(numerator, numberOfMethods);
            metricBuilder = metricBuilder.add(Metric.of("SIX", "Specialization Index", specializationIndex));
        }

        return metricBuilder.build();
    }

    private Pair<Integer, Integer> calculateOverloadedAndInheritedOperations(Type type) {

        Set<Triple<com.github.javaparser.ast.type.Type, String, List<com.github.javaparser.ast.type.Type>>> parentMethods = new HashSet<>();

        Stack<Type> typesToCheck = new Stack<>();

        typesToCheck.addAll(getParentTypes(type));

        while (!typesToCheck.empty()) {
            Type typeToCheck = typesToCheck.pop();

            for (Method method : typeToCheck.getMethods()) {
                Triple<com.github.javaparser.ast.type.Type, String, List<com.github.javaparser.ast.type.Type>> methodSignature = getMethodSignatureData(method);
                parentMethods.add(methodSignature);
            }

            typesToCheck.addAll(getParentTypes(typeToCheck));
        }

        int numberOverridden = 0;

        for (Method m : type.getMethods()) {

            Triple<com.github.javaparser.ast.type.Type, String, List<com.github.javaparser.ast.type.Type>> methodSignature = getMethodSignatureData(m);

            if (parentMethods.contains(methodSignature)) {
                numberOverridden++;
            }
        }

        return Pair.of(numberOverridden, parentMethods.size() - numberOverridden);
    }

    private Triple<com.github.javaparser.ast.type.Type, String, List<com.github.javaparser.ast.type.Type>> getMethodSignatureData(Method method) {
        com.github.javaparser.ast.type.Type returnType = method.getSource().getType();
        String name = method.getSource().getName().getIdentifier();
        List<com.github.javaparser.ast.type.Type> parameterTypes = method.getSource().getParameters().stream().map(parameter -> parameter.getType()).collect(Collectors.toList());

        return Triple.of(returnType, name, parameterTypes);
    }

    private Set<Type> getParentTypes(Type type) {
        Graph<Type> typeGraph = CalculationUtils.getInheritanceGraph(type.getParentPackage().getParentProject());
        return typeGraph.predecessors(type);
    }

    private int calculateInheritanceDepth(Type type) {
        ClassOrInterfaceDeclaration decl = type.getSource();

        Collection<Type> nextLevelTypes = getParentTypes(type);


        List<Integer> maximums = new ArrayList<>();

        for (Type t : nextLevelTypes) {
            maximums.add(1 + calculateInheritanceDepth(t));
        }


        return maximums.stream().mapToInt(i -> i.intValue()).max().orElse(1);
    }


}
