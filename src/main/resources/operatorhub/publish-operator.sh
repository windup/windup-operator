#!/bin/sh -x

# This script will ease the process to publish the operator on operatorhub
#
# Usage :
#   $ ./publish-operator.sh 0.0.2
#
#   This will copy files in windup-operator/src/main/operatorhub/windup-operator/0.0.2 into the community-operators project and create a PR opening the github web page
#
#
# Prerequisites :
#   * install github cli : https://github.com/cli/cli
#   * fork project github.com/operator-framework/community-operators
#   * have the credentials configured in your /home/~/.gitconfig file
#       [user]
#	    name = {your name}
#	    email = {your email}
#	    signingkey = [ value retrieved following this https://docs.github.com/en/github/authenticating-to-github/telling-git-about-your-signing-key ]
#
#
# Steps :
#   1. clone the forked project of : operator-framework/community-operators
#   2. create a branch for this new version
#   3. copy contents of operatorhub/windup-operator/$version into that project
#   4. add all files to git stage
#   5. commit the changes , signing
#   6. push changes to the remote forked repo
#   7. create a PR against operator-framework/community-operators:master opening the web browser to confirm

newversion=$1

# clone community-operators user fork
rm -rf community-operators
git clone git@github.com:windup/community-operators.git
git remote add upstream git@github.com:operator-framework/community-operators.git

# create branch in it
cd community-operators
git checkout -b "windup-operator-$newversion" master

# copy files from windup-operator, for the specific version
cp ../windup-operator/$newversion  ./community-operators/windup-operator/$newversion -r

# commit
git add --all ./community-operators/windup-operator
git commit -a -s -m "Upgrade Windup Operator to $newversion in community-operators"

# push
git push --set-upstream origin "windup-operator-$newversion"

# create pull request with all info, opening web browser to confirm
gh pr create -w --title "Upgrade Windup Operator to $newversion in community-operators" --base master --body "$(cat ../publish-pr-body.md)"

# clean
cd ..
rm -rf community-operators
