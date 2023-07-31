@Grab('org.yaml:snakeyaml:1.33')
import org.yaml.snakeyaml.*
import groovy.yaml.*
import java.util.Map

def file = new File(this.args[0])

def fileReader = new FileReader(file)
def yaml = new Yaml().load(fileReader)

// Adding cluster permissions to be able to fetch host domain
yaml.spec.install.spec.clusterPermissions.rules[0][1] = [:]
yaml.spec.install.spec.clusterPermissions.rules[0][1].apiGroups = ['config.openshift.io']
yaml.spec.install.spec.clusterPermissions.rules[0][1].resources = ['ingresses']
yaml.spec.install.spec.clusterPermissions.rules[0][1].verbs = ['get', 'list']

DumperOptions options = new DumperOptions();
options.indent = 2
options.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
options.defaultScalarStyle = DumperOptions.ScalarStyle.PLAIN
options.prettyFlow = true

new Yaml(options).dump(yaml, new FileWriter(file))
