import hudson.model.FreeStyleProject
import hudson.model.Cause
import hudson.tasks.Shell
import jenkins.install.InstallState
import jenkins.model.Jenkins

def instance = Jenkins.get()

def realm = new hudson.security.HudsonPrivateSecurityRealm(false)
def adminUser = realm.getUser('admin')
if (adminUser == null) {
  adminUser = realm.createAccount('admin', 'Admin123!')
} else {
  adminUser.addProperty(hudson.security.HudsonPrivateSecurityRealm.Details.fromPlainPassword('Admin123!'))
  adminUser.save()
}
instance.setSecurityRealm(realm)

def strategy = new hudson.security.FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)
instance.setInstallState(InstallState.INITIAL_SETUP_COMPLETED)

def jobName = 'security-and-devops-starter'
def job = instance.getItem(jobName)
if (job == null) {
  job = instance.createProject(FreeStyleProject, jobName)
}

job.setDescription('Builds and tests the Security and DevOps Spring Boot starter project from the Docker-mounted workspace.')
job.buildersList.clear()
job.buildersList.add(new Shell('''#!/bin/sh
set -eu
cd /workspace/security-and-devops/starter
rm -rf "$WORKSPACE/source"
mkdir -p "$WORKSPACE/source"
tar --exclude='./target' --exclude='./.git' -cf - . | tar -xf - -C "$WORKSPACE/source"
cd "$WORKSPACE/source"
mvn -B clean verify
'''))
job.save()

instance.save()

if ((job.getLastBuild() == null || job.getLastBuild().getResult()?.toString() != 'SUCCESS') && !job.isInQueue()) {
  job.scheduleBuild2(10, new Cause.UserIdCause())
}
