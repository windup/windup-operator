while getopts u:v:m: flag
do
    case "${flag}" in
        u) githubuser=${OPTARG};;
        v) newversion=${OPTARG};;
    esac
done

# Install GitHub cli to be able to create the pull request
# https://github.com/cli/cli

# clone community-operators user fork
git clone git@github.com:$githubuser/community-operators.git
git remote add upstream git@github.com:operator-framework/community-operators.git

# create branch in it
cd community-operators
git branch -b "mta-operator-$mtaoperatorversion" master

# copy files from windup-operator, for the specific version
cp ../mta-operator/$newversion  ./community-operators/mta-operator/$newversion

# commit
git add --all ./community-operators/mta-operator 
git commit -am "Upgrade MTA Operator to $mtaoperatorversion in community-operators" -S

# push
git push --set-upstream origin "mta-operator-$mtaoperatorversion"

# create pull request
gh pr create --title "Upgrade MTA Operator to $mtaoperatorversion in community-operators" --base master --body "$(cat publish-pr-body.md)"
