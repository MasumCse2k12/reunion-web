// =============================================================================
//  Sammalani Alumni — build & deploy pipeline
//  Source: https://github.com/MasumCse2k12/reunion-web
// =============================================================================
//
//  Builds web/ and deploys it to Vercel. `main` goes to production; every other
//  branch gets a throwaway preview URL, which is what you send the committee
//  before you promote anything.
//
// -----------------------------------------------------------------------------
//  A. What the Jenkins machine needs
// -----------------------------------------------------------------------------
//   - Plugins: Pipeline, Git, Docker Pipeline. (Optional: GitHub, for webhooks.)
//   - Docker available to the Jenkins user:  sudo usermod -aG docker jenkins
//     Then restart Jenkins. Verify with:     sudo -u jenkins docker run --rm hello-world
//     No Docker? See section E.
//   - Outbound HTTPS to github.com, registry.npmjs.org and vercel.com.
//
// -----------------------------------------------------------------------------
//  B. The one secret: a Vercel token
// -----------------------------------------------------------------------------
//   1. Create a token at https://vercel.com/account/tokens (scope: Full Account).
//   2. Jenkins → Manage Jenkins → Credentials → System → Global credentials → Add:
//          Kind:  Secret text
//          Secret: <paste the Vercel token>
//          ID:     vercel-token          <-- the ID must match exactly
//   VERCEL_ORG_ID and VERCEL_PROJECT_ID are hardcoded below on purpose. They are
//   public identifiers — they say *which* project to deploy, not who may deploy
//   it. The token is the only thing that grants access, so it is the only thing
//   that lives in Jenkins credentials.
//
//   GitHub credentials are NOT needed while reunion-web is a public repository.
//   If you ever make it private, add a second credential:
//          Kind: Username with password
//          Username: MasumCse2k12
//          Password: <a GitHub personal access token with `repo` scope>
//          ID:       github-reunion-web
//   ...and set GIT_CREDENTIALS_ID below to 'github-reunion-web'.
//
// -----------------------------------------------------------------------------
//  C. Create the job — Multibranch (recommended)
// -----------------------------------------------------------------------------
//   Gives you a production deploy on `main` and an automatic preview URL for
//   every other branch and pull request.
//
//   Jenkins → New Item → name: reunion-web → **Multibranch Pipeline** → OK
//     Branch Sources → Add source → Git
//         Project Repository: https://github.com/MasumCse2k12/reunion-web.git
//         Credentials:        - none -            (public repo)
//     Build Configuration
//         Mode:        by Jenkinsfile
//         Script Path: Jenkinsfile
//     Scan Repository Triggers
//         [x] Periodically if not otherwise run — Interval: 5 minutes
//     Save. Jenkins scans the repo and builds `main` immediately.
//
//   For instant builds instead of 5-minute polling, add a webhook on GitHub:
//     Repo → Settings → Webhooks → Add webhook
//         Payload URL:  https://<your-jenkins-host>/github-webhook/
//         Content type: application/json
//         Events:       Just the push event
//   Your Jenkins must be reachable from the internet for this. If it is not,
//   leave the 5-minute scan on — for a project with one committer that is fine.
//
// -----------------------------------------------------------------------------
//  D. Create the job — single Pipeline (simpler, one branch)
// -----------------------------------------------------------------------------
//   Jenkins → New Item → name: reunion-web-deploy → **Pipeline** → OK
//     [x] This project is parameterised   (the parameters block below fills in
//                                          DEPLOY_BRANCH and SKIP_DEPLOY on the
//                                          first run — run it once, then the
//                                          "Build with Parameters" button appears)
//     Pipeline
//         Definition:  Pipeline script from SCM
//         SCM:         Git
//         Repository URL: https://github.com/MasumCse2k12/reunion-web.git
//         Credentials:    - none -
//         Branch:      */main
//         Script Path: Jenkinsfile
//     Save → Build Now.
//
//   The Checkout stage below also works if you paste this file straight into the
//   inline "Pipeline script" box: with no SCM bound to the job it clones
//   GIT_REPO at DEPLOY_BRANCH itself.
//
// -----------------------------------------------------------------------------
//  E. No Docker on the agent?
// -----------------------------------------------------------------------------
//   Replace the whole `agent { docker { … } }` block with `agent any`, and make
//   sure the agent has Node >= 22.12 on its PATH — Vite 8 refuses to start on
//   anything older, and Debian/Ubuntu ship Node 18. Either:
//       curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash - && sudo apt install -y nodejs
//   or install the NodeJS plugin, configure an installation named `node-22`,
//   and add this just under `agent`:
//       tools { nodejs 'node-22' }
//
// -----------------------------------------------------------------------------
//  F. First run — what you should see
// -----------------------------------------------------------------------------
//   Checkout → Install → Typecheck → Build → Deploy — production → Smoke test
//   and a build description of "production → https://…vercel.app".
//   If it dies in Install with EACCES, Docker is running the container as root
//   despite the args below — check that no global Jenkins config forces `-u 0`.
//   If it dies in Deploy with "Not authorized", the vercel-token credential ID
//   is wrong or the token was revoked.
// =============================================================================

pipeline {
  agent {
    docker {
      // The full image, not -slim / -alpine: those omit git, which `checkout scm` needs.
      image 'node:22'
      // Jenkins runs the container as its own uid, which owns no home directory
      // inside the image. Without these, npm and npx die with EACCES on /root
      // during the very first build. Writing caches to /tmp keeps the container
      // non-root, so nothing root-owned is left behind in a shared workspace.
      args '-e HOME=/tmp -e NPM_CONFIG_CACHE=/tmp/.npm -e XDG_CACHE_HOME=/tmp/.cache'
    }
  }

  parameters {
    string(
      name: 'DEPLOY_BRANCH',
      defaultValue: 'main',
      description: 'Branch to build. Ignored by Multibranch jobs, which already know their branch.'
    )
    booleanParam(
      name: 'SKIP_DEPLOY',
      defaultValue: false,
      description: 'Build and typecheck only — do not deploy to Vercel.'
    )
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20', daysToKeepStr: '30'))
    timeout(time: 20, unit: 'MINUTES')
  }

  environment {
    APP_DIR            = 'web'
    GIT_REPO           = 'https://github.com/MasumCse2k12/reunion-web.git'
    // Empty while the repository is public — see section B.
    GIT_CREDENTIALS_ID = ''
    VERCEL_ORG_ID      = 'team_K0rTR9Nb455AyzPAXgNebJxR'
    VERCEL_PROJECT_ID  = 'prj_rDfnxa5e6gAcYwC9pceiZj8B2Qr7'
    CI                 = 'true'
    NPM_CONFIG_FUND    = 'false'
    NPM_CONFIG_AUDIT   = 'false'
  }

  stages {

    stage('Checkout') {
      steps {
        script {
          // A Multibranch job — or "Pipeline script from SCM" — already has `scm`
          // bound. A job with this script pasted inline does not, so fall back to
          // cloning GIT_REPO directly rather than failing.
          try {
            checkout scm
          } catch (Exception ignored) {
            // Reflection on the exception would trip the Groovy sandbox; the
            // only thing that matters here is that `scm` was not available.
            echo "No SCM bound to this job — cloning ${env.GIT_REPO} directly."
            if (env.GIT_CREDENTIALS_ID?.trim()) {
              git url: env.GIT_REPO, branch: params.DEPLOY_BRANCH, credentialsId: env.GIT_CREDENTIALS_ID
            } else {
              git url: env.GIT_REPO, branch: params.DEPLOY_BRANCH
            }
          }

          // Multibranch sets BRANCH_NAME; the other two job types do not. Resolve
          // it once here so the deploy stages below have one thing to test.
          env.TARGET_BRANCH = env.BRANCH_NAME?.trim() ?: params.DEPLOY_BRANCH
          echo "Building branch: ${env.TARGET_BRANCH}"
        }
        sh 'git --no-pager log -1 --pretty="%h %an %s"'
      }
    }

    stage('Install') {
      steps {
        dir(env.APP_DIR) {
          sh 'node -v && npm -v'
          // `npm ci` not `npm install` — the lockfile is the contract.
          sh 'npm ci'
        }
      }
    }

    // Typecheck and build are separate on purpose: when this pipeline goes red
    // you want to know at a glance whether it was the types or the bundler.
    stage('Typecheck') {
      steps {
        dir(env.APP_DIR) {
          sh 'npm run typecheck'
        }
      }
    }

    stage('Build') {
      steps {
        dir(env.APP_DIR) {
          sh 'npm run build'
          sh 'ls -lh dist dist/assets'
        }
      }
      post {
        success {
          archiveArtifacts artifacts: "${env.APP_DIR}/dist/**", fingerprint: true
        }
      }
    }

    stage('Deploy — preview') {
      when {
        allOf {
          expression { return !params.SKIP_DEPLOY }
          expression { return env.TARGET_BRANCH != 'main' }
        }
      }
      steps {
        withCredentials([string(credentialsId: 'vercel-token', variable: 'VERCEL_TOKEN')]) {
          dir(env.APP_DIR) {
            sh '''
              set -e
              npx --yes vercel@latest pull  --yes --environment=preview --token="$VERCEL_TOKEN"
              npx --yes vercel@latest build --token="$VERCEL_TOKEN"
              npx --yes vercel@latest deploy --prebuilt --token="$VERCEL_TOKEN" > .deploy-url
            '''
            script {
              env.DEPLOY_URL = readFile('.deploy-url').trim()
              currentBuild.description = "preview → ${env.DEPLOY_URL}"
            }
          }
        }
      }
    }

    stage('Deploy — production') {
      when {
        allOf {
          expression { return !params.SKIP_DEPLOY }
          expression { return env.TARGET_BRANCH == 'main' }
        }
      }
      steps {
        withCredentials([string(credentialsId: 'vercel-token', variable: 'VERCEL_TOKEN')]) {
          dir(env.APP_DIR) {
            sh '''
              set -e
              npx --yes vercel@latest pull  --yes --environment=production --token="$VERCEL_TOKEN"
              npx --yes vercel@latest build --prod --token="$VERCEL_TOKEN"
              npx --yes vercel@latest deploy --prebuilt --prod --token="$VERCEL_TOKEN" > .deploy-url
            '''
            script {
              env.DEPLOY_URL = readFile('.deploy-url').trim()
              currentBuild.description = "production → ${env.DEPLOY_URL}"
            }
          }
        }
      }
    }

    stage('Smoke test') {
      when {
        expression { return env.DEPLOY_URL?.trim() }
      }
      steps {
        // A green build that serves a blank page is worse than a red one.
        sh '''
          set -e
          for path in / /admin/login /app/profile; do
            code=$(curl -s -o /dev/null -w '%{http_code}' "$DEPLOY_URL$path")
            echo "$path -> $code"
            [ "$code" = "200" ] || { echo "FAILED: $path returned $code"; exit 1; }
          done
        '''
      }
    }
  }

  post {
    success {
      echo "OK  ${env.TARGET_BRANCH ?: 'build'}${env.DEPLOY_URL ? " — ${env.DEPLOY_URL}" : ' — built, not deployed'}"
    }
    failure {
      echo "FAILED  ${env.TARGET_BRANCH ?: 'build'} — see the stage log above."
    }
    always {
      // .vercel/ holds a pulled project link; never leave it in a shared workspace.
      dir(env.APP_DIR) {
        sh 'rm -rf .vercel .deploy-url || true'
      }
    }
  }
}
