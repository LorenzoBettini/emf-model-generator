# BPMN cross-resource round-trip investigation

## Configuration and result

The integration tests use `src/test/resources/external-metamodels/BPMN.ecore`,
package `bpmn`, and root class `BpmnDiagram`. They generate five roots at maximum
containment depth 2, with default maximum count 1 for attributes, containment
references, cross-references, and FeatureMaps. This produces 80 EObjects in five
XMI resources. The single-root control changes only the root count. All cases use
the generator's deterministic default values; no BPMN-specific value functions
are installed.

The five-resource graph passes in-memory EMF validation. Ordinary
`saveAndValidateRoundTrip()` saves and reloads all five resources, but returns an
invalid result. The diagnostics include unresolved proxies in `associations`,
`incomingEdges`, `activities`, `lanes`, and other references, plus inconsistent
messaging opposites. These are two related reconstruction problems, rather than
missing generated target objects.

| Experiment | In-memory | Fresh load / standard round trip | Finding |
| --- | --- | --- | --- |
| One root, one resource | Valid | Valid | BPMN serialization works when this graph has no inter-resource references. |
| Five roots, separate resources, defaults | Valid | Invalid | Nonresolving references retain proxies; some messaging opposites are inconsistent. |
| `EcoreUtil.resolveAll` after fresh load | Valid before save | Invalid | Some resolving references change, but nonresolving proxy values remain. |
| `EcoreUtil.resolve(proxy, resourceSet)` | Valid before save | Original reference still holds proxy | An individual target can be found without replacing its owner feature. |
| Test-local post-reload repair | Valid before save | Valid **after repair only** | Replace resolvable proxies, then reassign messaging endpoints through ordinary EMF inverse notifications. |
| Five roots moved to one resource | Valid | Valid | All five roots reload; no unresolved proxies remain. |
| Five separate resources, resource-aware selection | Valid | Valid | Local candidates for vulnerable references avoid these reconstruction failures. |

## Why the proxies persist

`BPMN.ecore` declares `Activity.groups`, `Activity.lanes`,
`AssociationTarget.associations`, `Vertex.outgoingEdges`, and
`Vertex.incomingEdges` with `resolveProxies=false`. `Group.activities` and
`Lane.activities` have resolving declarations, but their opposites on `Activity`
do not. `Association.source` and `Association.target` are transient and
nonresolving opposites. `MessageVertex.incomingMessages` and
`outgoingMessages` are transient, derived views of `orderedMessages`, with
`MessagingEdge.target` and `source` as their serialized opposites.

For example, a reloaded `Vertex.incomingEdges` value had a URI ending in
`bpmn_BpmnDiagram_2.xmi#name15`, and a `Vertex.associations` value pointed to
`bpmn_BpmnDiagram_3.xmi#//@artifacts.0/@associations.0`. Another
`Lane.activities` value pointed to `bpmn_BpmnDiagram_2.xmi#name10`. The URI
fragments and feature kinds come from the deterministic five-resource test;
the full file URI starts with the test output directory. The target resources
were already loaded in the fresh ResourceSet, and `getEObject(proxyURI, true)`
returned real, nonproxy EObjects in those resources. These values represent
**existing targets held as proxies**, not missing targets.

`EcoreUtil.resolveAll(resourceSet)` does not replace every nonresolving feature
value. Direct `EcoreUtil.resolve(proxy, resourceSet)` returns the expected real
target, while the original feature still contains the proxy. Thus automatic
resolution and explicit resolution have different effects here.

The test-local repair uses EMF list `set`/`remove` and `eSet` for changeable,
nonderived, noncontainment references only. Removing a proxy already represented
by its target avoids violating EMF's unique-list constraint. This eliminates
the observed proxies, but validation still fails on `MessagingEdge.source` and
`target` versus the derived `MessageVertex` message views. Clearing and
reassigning those endpoints through `eSet` invokes EMF's normal inverse and
FeatureMap updates; only then does the reconstructed model validate. This is a
**post-reload repair**. It does not make the original five-resource XMI pass
strict, ordinary `saveAndValidateRoundTrip()`.

## Serialization and selection alternatives

Moving the original five root objects into one XMI resource preserves their
identities. The four emptied model resources are removed from the generator's
ResourceSet, leaving the Ecore resource in place. The shared XMI reloads all
five roots, has no unresolved proxies, has consistent opposites, and passes the
standard round trip without repair. Keeping the empty model resources would
make `modelResources()` save them too, so their removal is part of the test.

The separate-resource test installs a test-local `EMFCrossReferenceSetter` through
the public populator API. For a reference whose own declaration or opposite is
nonresolving, transient, or derived, it selects a compatible candidate in the
owner's resource, respecting opposite multiplicity and excluding self-links.
Other references retain the ordinary selector. This covers the FeatureMap backed
message opposites as well as nonresolving direct references. With the same 80
objects in five resources, both in-memory validation and strict standard round
trip succeed; all checked opposite relationships remain consistent. A policy
that considers only `resolveProxies=false` still leaves messaging inverse
failures, because the derived/transient opposite views also matter.

## Possible library changes

| Option | Advantage | Cost and compatibility |
| --- | --- | --- |
| Leave the library unchanged; document one-resource layout or test-local policy | Preserves current cross-resource candidate distribution and API behavior. | Callers must recognize metamodels with nonresolving or derived opposites. |
| Add an opt-in resource-aware cross-reference policy | Could keep separate resources strictly valid for metamodels like BPMN. | Needs access to the actual reference, both opposite declarations, and FeatureMap semantics; same-resource candidates may not satisfy every required reference. Changing the default would alter generated graphs. |
| Add an explicit post-load repair utility | Could help consumers that control the reload step. | Mutates the reconstructed model and may require feature-specific inverse updates; cannot count as ordinary round-trip validation. |

No production change is justified by this investigation alone. The two supported
workflows already provide valid output, and changing default selection would
change existing model topology. An opt-in resource-aware policy could be
considered if other metamodels show the same pattern, with required-reference
behavior and FeatureMap opposites specified and tested before adding an API.
