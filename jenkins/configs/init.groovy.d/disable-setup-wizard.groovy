// Disable Jenkins Setup Wizard
import jenkins.model.*
import hudson.util.*

def instance = Jenkins.getInstance()

// Skip setup wizard
instance.setInstallState(InstallState.INITIAL_SETUP_COMPLETED)

instance.save()

println "Setup wizard disabled"

