@Grab('org.yaml:snakeyaml:1.33')
import org.yaml.snakeyaml.*
import groovy.yaml.*
import java.time.LocalDate

def file = new File(this.args[0])
def version = this.args[1]

def fileReader = new FileReader(file)
def yaml = new Yaml().load(fileReader)

yaml.metadata.name = 'windup-operator.v' + version
yaml.spec.annotations.containerImage = 'quay.io/windupeng/windup-operator-native:' + version
yaml.spec.annotations.description = 'Windup Operator.'
yaml.spec.annotations.createdAt = LocalDate.now().toString()
// yaml.spec.install.spec.deployments[0].spec.template.spec.containers[0].image = 'quay.io/windupeng/windup-operator-native:' + version
yaml.spec.version = version
yaml.spec.customresourcedefinitions.owned[0].displayName = 'Windup'
yaml.spec.customresourcedefinitions.owned[0].description = 'Windup'

// Workaround for moving annotations since OperatorHub.io complains about it
yaml.metadata.annotations = yaml.spec.annotations
yaml.spec.annotations = ""

DumperOptions options = new DumperOptions();
options.indent = 2
options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
options.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
options.prettyFlow = true

new Yaml(options).dump(yaml, new FileWriter(file))
