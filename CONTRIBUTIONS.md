Для каждой версии идеи есть свои ветки dev и master. Например, для версии 2021.3.3 созданы ветки 213/dev и 213/master.

Разработка ведётся в */dev. В ветки */master можно только мержить соотвествующие dev ветки с помощью создания pull request в гитхабе.

Flow разработки новой версии:
1. Все ветки dev смержить с соотвествующим master, в случае если после предыдущего релиза они не совпадают;
2. В гитхабе создаётся issue в которой описывается задача/баг/т.п.;
3. Выбирается версия IDEA в которой вам удобнее разрабатывать и от dev этой версии отводите ветку для выполнения issue;
4. Когда issue выполнена и протестирована работа IDEA в отведённой ветке, то надо сделать rebase на dev ветку. После чего сделан push, чтобы github actions собрал и проверил версию.
5. Далее нужно распространить ветку с фичёй на другие версии. Чаще всего вам нужно будет просто cherry pick (возможно вместе со squash) на другие dev ветки. Но иногда новый код будет не совместим с другими версиями и придётся его переписывать и тут на ваше усмотрение как лучше делать. Единственное условие что история в репозитории старается ввестись без merge commits.
6. После того, как все фичи запланированные для версии будут реализованы, то создаётся PR по мержу devs в masters. Посмотрите как оформленны предыдущие PR и сделайте на подобие;
7. После того как PR будет создан, тогда запустится github actions который ещё раз проверит совместимость с версиями и соберет образ плагина, который вы можете вручную установить на свою IDEA.
8. После одобрения PR, смёржете, уделите внимание чтобы не создался отдельный merge commit, а merge был осуществлён с помощью fast forward. В этот момент GA опубликует плагин в EAP канал и создаст draft release. 
9. После проверки работы плагина и готовности опубликовать плагин в Stable канал, то нужно зайти в draft release и опубликовать релиз. Сразу после этого GA опубликует плагин в Stable канал.
   
Each version of the idea has its own dev and master branches. For example, branches 213/dev and 213/master are created for version 2021.3.3.

Development is done in */dev. The */master branch can only be moderated to the corresponding dev branches by creating a pull request in the githab.

New version development flow:
1. All dev branches will die with the corresponding master if they do not match after the previous release;
2. An issue is created in the githab which describes the task/bug/etc;
3. Choose the IDEA version you are more comfortable to develop in and from dev of this version take a branch to run the issue;
4. When the issue is done and IDEA is tested in the branch, you have to rebase to the dev branch. After that a push is made to have github actions build and verify the version.
5. Next you need to propagate the branch with the feature to other versions. Most of the time you will just need to cherry pick (possibly along with squash) the other dev branches. But sometimes the new code will be incompatible with other versions and you will have to rewrite it, so this is up to you. The only condition is that the story in the repository tries to be entered without merge commits.
6. After all the features planned for the version are implemented, a PR is created for the devs merge into masters. Look at previous PRs and make one like it;
7. After the PR is created, then the github actions will run, which will once again check the compatibility with the versions and build an image of the plugin, which you can manually install on your IDEA.
8. Once the PR is approved, you will merge, take care not to create a separate merge commit, but merge using fast forward. At this point GA will publish the plugin in the EAP channel and create a draft release.
9. After checking the plugin's work and readiness to publish the plugin in the Stable channel, you need to go into the draft release and publish the release. Immediately after that GA will publish the plugin into the Stable channel.