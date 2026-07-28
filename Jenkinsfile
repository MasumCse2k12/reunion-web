// =============================================================================
//  Sammalani Alumni — build & deploy pipeline
// =============================================================================
//  Builds web/ and deploys it to Vercel. `main` goes to production; every other
//  branch gets a throwaway preview URL, which is what you send the committee
//  before you promote anything.
//
//  ---------------------------------------------------------------------------
//  One-time Jenkins setup
//  ---------------------------------------------------------------------------
//  1. Plugins: Pipeline, Git, Docker Pipeline.
//  2. Credentials → System → Global → Add → **Secret text**
//         ID:     vercel-token
//         Secret: a Vercel token from https://vercel.com/account/tokens
//     That token is the only secret this pipeline needs. VERCEL_ORG_ID and
//     VERCEL_PROJECT_ID below are public identifiers — they say *which* project
//     to deploy, not who is allowed to.
//  3. New Item → Multibranch Pipeline → point it at
//     https://github.com/MasumCse2k12/reunion-web
//     Script path: Jenkinsfile
//
//  If the Jenkins agent has no Docker, delete the `agent { docker { … } }` block
//  and use `agent any` instead — but the agent must then have Node >= 22.12
//  installed, because Vite 8 will not start on anything older.
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

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20', daysToKeepStr: '30'))
    timeout(time: 20, unit: 'MINUTES')
  }

  environment {
    APP_DIR           = 'web'
    VERCEL_ORG_ID     = 'team_K0rTR9Nb455AyzPAXgNebJxR'
    VERCEL_PROJECT_ID = 'prj_rDfnxa5e6gAcYwC9pceiZj8B2Qr7'
    CI                = 'true'
    NPM_CONFIG_FUND   = 'false'
    NPM_CONFIG_AUDIT  = 'false'
  }

  stages {

    stage('Checkout') {
      steps {
        checkout scm
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
          archiveArtifacts artifacts: "${env.APP_DIR}/dist/**", fingerprint: true, onlyIfSuccessful: true
        }
      }
    }

    stage('Deploy — preview') {
      when {
        beforeAgent true
        not { branch 'main' }
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
        beforeAgent true
        branch 'main'
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
        beforeAgent true
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
      echo "✅ ${env.BRANCH_NAME ?: 'build'} ok${env.DEPLOY_URL ? " — ${env.DEPLOY_URL}" : ''}"
    }
    failure {
      echo "❌ ${env.BRANCH_NAME ?: 'build'} failed at stage: ${currentBuild.description ?: 'see log'}"
    }
    always {
      // .vercel/ holds a pulled project link; never leave it in a shared workspace.
      dir(env.APP_DIR) {
        sh 'rm -rf .vercel .deploy-url || true'
      }
    }
  }
}
