/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.configurations.ResolutionResultProvider
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.internal.Describables
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class DefaultArtifactVariantSelectorFactoryTest extends Specification {
    def matchingCache = Mock(ConsumerProvidedVariantFinder)
    def producerSchema = Mock(ImmutableAttributesSchema)
    def consumerSchema = Mock(ImmutableAttributesSchema)
    def attributeMatcher = Mock(AttributeMatcher)
    def schemaServices = Mock(AttributeSchemaServices) {
        getMatcher(consumerSchema, producerSchema) >> attributeMatcher
    }
    def factory = Mock(ArtifactVariantSelector.ResolvedArtifactTransformer)
    def transformedVariantFactory = Mock(TransformedVariantFactory)
    def variantSelectionFailureProcessor = DependencyManagementTestUtil.newFailureHandler()
    def variantSelectorFactory = new DefaultVariantSelectorFactory(
        matchingCache,
        AttributeTestUtil.attributesFactory(),
        schemaServices,
        transformedVariantFactory,
        variantSelectionFailureProcessor,
        StandaloneDomainObjectContext.ANONYMOUS,
        TestUtil.calculatedValueContainerFactory(),
        TestUtil.taskDependencyFactory()
    )

    def "selects producer variant with requested attributes"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def variant1Artifacts = Stub(ResolvedArtifactSet)
        def set = resolvedVariantSet()
        def variants = [variant1, variant2]

        given:
        set.schema >> producerSchema
        set.variants >> variants
        variant1.attributes >> typeAttributes("classes")
        variant1.artifacts >> variant1Artifacts
        variant2.attributes >> typeAttributes("jar")

        attributeMatcher.matchMultipleCandidates(_ as Collection, typeAttributes("classes"), _ as AttributeMatchingExplanationBuilder) >> [variant1]

        expect:
        def result = newSelector().select(set, typeAttributes("classes"), false, factory)
        result == variant1Artifacts
    }

    def "fails when multiple producer variants match"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2]

        given:
        set.asDescribable() >> Describables.of('<component>')
        set.schema >> producerSchema
        set.variants >> variants
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant1.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')
        variant2.attributes >> typeAttributes("jar")

        attributeMatcher.matchMultipleCandidates(_ as Collection, typeAttributes("classes"), _ as AttributeMatchingExplanationBuilder) >> [variant1, variant2]
        attributeMatcher.isMatchingValue(_, _, _) >> true

        when:
        def result = newSelector().select(set, typeAttributes("classes"), false, factory)
        visit(result)

        then:
        def e = thrown(ArtifactSelectionException)
        e.message == toPlatformLineSeparators("""The consumer was configured to find attribute 'artifactType' with value 'classes'. However we cannot choose between the following variants of <component>:
  - <variant1> declares attribute 'artifactType' with value 'classes'
  - <variant2> declares attribute 'artifactType' with value 'jar'""")
    }

    def "fails when multiple transforms match"() {
        def requested = typeAttributes("dll")
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2]
        def transformedVariants = variants.collect { transformedVariant(it, it.attributes, requested)}

        given:
        set.schema >> producerSchema
        set.variants >> variants
        set.asDescribable() >> Describables.of('<component>')
        variant1.attributes >> typeAttributes("jar")
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant2.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')

        attributeMatcher.matchMultipleCandidates(ImmutableList.copyOf(variants), _, _) >> []
        attributeMatcher.matchMultipleCandidates(transformedVariants, _, _) >> transformedVariants
        matchingCache.findTransformedVariants(_, _) >> transformedVariants

        def selector = newSelector()

        when:
        def result = selector.select(set, requested, false, factory)
        visit(result)

        then:
        def e = thrown(ArtifactSelectionException)
        e.message == toPlatformLineSeparators("""Found multiple transforms that can produce a variant of <component> with requested attributes:
  - artifactType 'dll'
Found the following transforms:
  - From '<variant1>':
      - With source attributes: artifactType 'jar'
      - Candidate transform(s):
          - Transform '' producing attributes: artifactType 'dll'
  - From '<variant2>':
      - With source attributes: artifactType 'classes'
      - Candidate transform(s):
          - Transform '' producing attributes: artifactType 'dll'""")
    }

    def "returns no matching variant artifact set when no variants match and ignore no matching enabled"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2]

        given:
        set.schema >> producerSchema
        set.variants >> variants
        variant1.attributes >> typeAttributes("jar")
        variant2.attributes >> typeAttributes("classes")

        attributeMatcher.matchMultipleCandidates(_, _, _) >> []

        matchingCache.findTransformedVariants(_, _) >> []

        expect:
        def result = newSelector().select(set, typeAttributes("dll"), true, factory)
        result == ResolvedArtifactSet.EMPTY
    }

    def "fails when no variants match and ignore no matching disabled"() {
        def variant1 = resolvedVariant()
        def variant2 = resolvedVariant()
        def set = resolvedVariantSet()
        def variants = [variant1, variant2]

        given:
        set.schema >> producerSchema
        set.variants >> variants
        set.asDescribable() >> Describables.of('<component>')
        variant1.attributes >> typeAttributes("jar")
        variant1.asDescribable() >> Describables.of('<variant1>')
        variant2.attributes >> typeAttributes("classes")
        variant2.asDescribable() >> Describables.of('<variant2>')

        attributeMatcher.matchMultipleCandidates(_, _, _) >> []

        matchingCache.findTransformedVariants(_, _) >> []

        when:
        def result = newSelector().select(set, typeAttributes("dll"), false, factory)
        visit(result)

        then:
        def e = thrown(ArtifactSelectionException)
        e.message == toPlatformLineSeparators("""No variants of  match the consumer attributes:
  - <variant1>:
      - Incompatible because this component declares attribute 'artifactType' with value 'jar' and the consumer needed attribute 'artifactType' with value 'dll'
  - <variant2>:
      - Incompatible because this component declares attribute 'artifactType' with value 'classes' and the consumer needed attribute 'artifactType' with value 'dll'""")
    }

    private ArtifactVariantSelector newSelector() {
        variantSelectorFactory.create(
            Mock(ResolutionHost),
            ImmutableAttributes.EMPTY,
            consumerSchema,
            null,
            ResolutionStrategy.SortOrder.DEFAULT,
            Mock(ResolutionResultProvider),
            Mock(ResolutionResultProvider)
        )
    }

    private ResolvedVariant resolvedVariant() {
        Stub(ResolvedVariant)
    }

    private ResolvedVariantSet resolvedVariantSet() {
        Stub(ResolvedVariantSet) {
            getOverriddenAttributes() >> ImmutableAttributes.EMPTY
        }
    }

    def visit(ResolvedArtifactSet set) {
        def artifactVisitor = Stub(ArtifactVisitor)
        _ * artifactVisitor.visitFailure(_) >> { Throwable t -> throw t }
        def visitor = Stub(ResolvedArtifactSet.Visitor)
        _ * visitor.visitArtifacts(_) >> { ResolvedArtifactSet.Artifacts artifacts -> artifacts.visit(artifactVisitor) }
        set.visit(visitor)
    }

    private static ImmutableAttributes typeAttributes(String artifactType) {
        def attributeContainer = AttributeTestUtil.attributesFactory().mutable()
        attributeContainer.attribute(ARTIFACT_TYPE_ATTRIBUTE, artifactType)
        attributeContainer.asImmutable()
    }

    TransformedVariant transformedVariant(ResolvedVariant root, AttributeContainerInternal fromAttributes, AttributeContainerInternal toAttributes) {
        ImmutableAttributes attrs = toAttributes.asImmutable()
        Transform transform = Mock(Transform) {
            getDisplayName() >> ""
            getFromAttributes() >> fromAttributes
            getToAttributes() >> toAttributes
        }
        TransformStep step = Mock(TransformStep) {
            getDisplayName() >> ""
            getTransform() >> transform
            getFromAttributes() >> fromAttributes
        }
        VariantDefinition definition = Mock(VariantDefinition) {
            getTransformChain() >> new TransformChain(null, step)
            getTargetAttributes() >> attrs
        }
        return new TransformedVariant(root, definition)
    }
}
