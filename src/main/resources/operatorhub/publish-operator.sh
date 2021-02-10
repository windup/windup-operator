while getopts u:v:m: flag
do
    case "${flag}" in
        u) githubuser=${OPTARG};;
        m) mtaoperatorversion=${OPTARG};;
    esac
done

# clone community-operators user fork
git clone git@github.com:$githubuser/community-operators.git
git remote add upstream git@github.com:operator-framework/community-operators.git

# create branch in it
cd community-operators
git branch -b "mta-operator-$mtaoperatorversion" master

# copy files from windup-operator, for the certain version
cp ../mta-operator/$mtaoperatorversion  ./community-operators/mta-operator 

# commit
git add --all ./community-operators/mta-operator 
git commit -am "Upgrade MTA Operator to $mtaoperatorversion in community-operators"

# push
git push --set-upstream origin "mta-operator-$mtaoperatorversion"

# create pull request
git request-pull upstream master 
