#!/usr/bin/env python
##
## (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
##
## All rights reserved. This program and the accompanying materials
## are made available under the terms of the GNU Lesser General Public License
## (LGPL) version 2.1 which accompanies this distribution, and is available at
## http://www.gnu.org/licenses/lgpl.html
##
## This library is distributed in the hope that it will be useful,
## but WITHOUT ANY WARRANTY; without even the implied warranty of
## MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
## Lesser General Public License for more details.
##
## Contributors:
##     Julien Carsique
##
## Utilities for Python scripts.
##
from zipfile import ZIP_DEFLATED
from zipfile import ZipFile
import optparse
import os
import platform
import re
import shlex
import subprocess
import sys
import time
import shutil


class ExitException(Exception):
    def __init__(self, return_code, message=None):
        self.return_code = return_code
        self.message = message


class Repository(object):
    """Nuxeo repository manager.

    Provides recursive Git and Shell functions."""

    def __init__(self, basedir, alias):
        assert_git_config()
        self.driveletter = long_path_workaround_init()
        self.basedir = basedir
        self.alias = alias
        # find the remote URL
        remote_lines = check_output(["git", "remote", "-v"]).split("\n")
        for remote_line in remote_lines:
            remote_alias, remote_url, _ = remote_line.split()
            if alias == remote_alias:
                break

        self.is_online = remote_url.endswith("/nuxeo.git")
        if self.is_online:
            self.url_pattern = re.sub("(.*)nuxeo", r"\1module", remote_url)
        else:
            self.url_pattern = remote_url + "/module"
        self.modules = []
        self.addons = []

    def cleanup(self):
        if hasattr(self, "driveletter"):
            long_path_workaround_cleanup(self.driveletter, self.basedir)

    def eval_modules(self):
        """Set the list of Nuxeo addons in 'self.modules'."""
        os.chdir(self.basedir)
        self.modules = []
        log("Using maven introspection of the pom.xml files"
            " to find the list of sub-repositories")
        for line in os.popen("mvn -N help:effective-pom"):
            line = line.strip()
            m = re.match("<module>(.*?)</module>", line)
            if not m:
                continue
            self.modules.append(m.group(1))

    def eval_addons(self, with_optionals=False):
        """Set the list of Nuxeo addons in 'self.addons'.

        If 'with_optionals', add "optional" addons to the list."""
        os.chdir(os.path.join(self.basedir, "addons"))
        self.addons = []
        log("Using maven introspection of the pom.xml files"
            " to find the list of addons")
        all_lines = os.popen("mvn -N help:effective-pom").readlines()
        if with_optionals:
            all_lines += os.popen("mvn -N help:effective-pom " +
                                  "-f pom-optionals.xml").readlines()
        for line in all_lines:
            line = line.strip()
            m = re.match("<module>(.*?)</module>", line)
            if not m:
                continue
            self.addons.append(m.group(1))

    def git_pull(self, module, version):
        """Git clone or fetch, then update.

        'module': the Git module to run on.
        'version': the version to checkout."""
        repo_url = self.url_pattern.replace("module", module)
        cwd = os.getcwd()
        if os.path.isdir(module):
            log("Updating " + module + "...")
            os.chdir(module)
            system("git fetch %s" % (self.alias))
        else:
            log("Cloning " + module + "...")
            system("git clone %s" % (repo_url))
            os.chdir(module)
        self.git_update(version)
        os.chdir(cwd)

    def system_recurse(self, command, with_optionals=False):
        """Execute the given command on current and sub-repositories.

        'command': the command to execute.
        If 'with_optionals', also recurse on "optional" addons."""
        cwd = os.getcwd()
        os.chdir(self.basedir)
        system(command)
        if not self.modules:
            self.eval_modules()
        for module in self.modules:
            os.chdir(os.path.join(self.basedir, module))
            system(command)
        if not self.addons:
            self.eval_addons(with_optionals)
        for addon in self.addons:
            os.chdir(os.path.join(self.basedir, "addons", addon))
            system(command)
        os.chdir(cwd)

    def archive(self, archive, version=self.get_current_version(),
                with_optionals=False):
        """Archive the sources of current and sub-repositories.

        'archive': full path of archive to generate.
        If 'with_optionals', also recurse on "optional" addons."""
        archive_dir = os.path.join(os.path.dirname(archive), "sources")
        cwd = os.getcwd()
        os.chdir(self.basedir)
        if os.path.isdir(archive_dir):
            shutil.rmtree(archive_dir)
        os.mkdir(archive_dir)
        system("git archive %s|(cd %s && tar xf -)||exit 1"
               % (version, archive_dir))
        if not self.modules:
            self.eval_modules()
        for module in self.modules:
            os.chdir(os.path.join(self.basedir, module))
            system("git archive --prefix=%s/ %s|(cd %s && tar xf -)||exit 1"
                   % (module, version, archive_dir))
        if not self.addons:
            self.eval_addons(with_optionals)
        for addon in self.addons:
            os.chdir(os.path.join(self.basedir, "addons", addon))
            system("git archive --prefix=%s/ %s|(cd %s && tar xf -)||exit 1"
                   % (addon, version, os.path.join(archive_dir, "addons")))
        make_zip(archive, archive_dir)
        shutil.rmtree(archive_dir)
        os.chdir(cwd)

    def git_update(self, version):
        """Git update using checkout, stash (if needed) and rebase.

        'version': the version to checkout."""
        if version in check_output(["git", "tag"]).split():
            # the version is a tag name
            system("git checkout %s" % version)
        elif version not in check_output(["git", "branch"]).split():
            # create the local branch if missing
            system("git checkout --track -b %s %s/%s" % (version, self.alias,
                                                         version))
        else:
            # reuse local branch
            system("git checkout %s" % version)
            log("Updating branch")
            retcode = system("git rebase %s/%s" % (self.alias, version), False)
            if retcode != 0:
                system("git stash")
                system("git rebase %s/%s" % (self.alias, version))
                system("git stash pop -q")
        log("")

    def clone(self, version, with_optionals=False):
        """Clone or update whole Nuxeo repository.

        'version': the version to checkout.
        If 'with_optionals', also clone/update "optional" addons."""
        log("Cloning/updating parent pom")
        system("git fetch %s" % (self.alias))
        self.git_update(version)

        # Main modules
        self.eval_modules()
        for module in self.modules:
            self.git_pull(module, version)

        # Addons
        cwd = os.getcwd()
        os.chdir(os.path.join(self.basedir, "addons"))
        self.eval_addons(with_optionals)
        if not self.is_online:
            self.url_pattern = self.url_pattern.replace("module",
                                                        "addons/module")
        for addon in self.addons:
            self.git_pull(addon, version)
        if not self.is_online:
            self.url_pattern = self.url_pattern.replace("addons/module",
                                                        "module")
        os.chdir(cwd)

    def get_current_version():
        """Return branch or tag version of current Git workspace."""
        t = check_output(["git", "describe", "--all"]).split("/")
        return t[1]


def log(message, out=sys.stdout):
    out.write(message + os.linesep)
    out.flush()


def system(cmd, failonerror=True, delay_stdout=True):
    """Shell execution.

    'cmd': the command to execute.
    If 'failonerror', command execution failure raises an ExitException.
    If 'delay_stdout', output is flushed at the end of command execution."""
    log("$> " + cmd)
    args = shlex.split(cmd)
    if delay_stdout:
        p = subprocess.Popen(args, stdin=subprocess.PIPE,
                             stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        out, err = p.communicate()
        sys.stdout.write(out)
        sys.stdout.flush()
    else:
        p = subprocess.Popen(args)
        p.wait()
    retcode = p.returncode
    if retcode != 0:
        if failonerror:
            raise ExitException(retcode,
                                "Command returned non-zero exit code: %s"
                                % cmd)
    return retcode


def system_with_retries(cmd, failonerror=True):
    """Shell execution with ten retries in case of failures.

    'cmd': the command to execute.
    If 'failonerror', latest command execution failure raises an ExitException.
    """
    retries = 0
    while True:
        retries += 1
        retcode = system(cmd, False)
        if retcode == 0:
            return 0
        elif retries > 10:
            return system(cmd, failonerror)
        else:
            log("Error executing %s - retrying in 10 seconds..." % cmd,
                sys.stderr)
            time.sleep(10)


def long_path_workaround_init():
    """Windows only. Try to map the current directory to an unused drive letter
    to shorten path names."""
    if platform.system() != "Windows":
        return
    for letter in "GHIJKLMNOPQRSTUVWXYZ":
        if not os.path.isdir("%s:\\" % (letter,)):
            driveletter = letter
            cwd = os.getcwd()
            system("SUBST %s: \"%s\"" % (driveletter, cwd))
            time.sleep(10)
            os.chdir("%s:\\" % (driveletter,))
            break
    return driveletter


def long_path_workaround_cleanup(driveletter, basedir):
    """Windows only. Cleanup the directory mapping if any."""
    if driveletter != None:
        os.chdir(basedir)
        system("SUBST %s: /D" % (driveletter,), False)


def check_output(cmd):
    """Return Shell command output."""
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    out, err = p.communicate()
    if err != None:
        log("[ERROR] Command", str(cmd), " returned an error:", sys.stderr)
        log(err, sys.stderr)
    return out.strip()


def assert_git_config():
    """Check Git configuration."""
    t = check_output(["git", "config", "--get", "color.branch"])
    if "always" in t:
        raise ExitException(1, "The git color mode must not be always, try:" +
                            "\n git config --global color.branch auto" +
                            "\n git config --global color.status auto")


def make_zip(archive, root_dir=None, base_dir=os.curdir, mode="w"):
    """Create a zip file from all the files under 'root_dir'/'base_dir'.

    If 'root_dir' is not specified, it uses the current directory.
    If 'base_dir' is not specified, it uses the current directory constant '.'.
    The 'mode' must be 'w' (write) or 'a' (append)."""
    cwd = os.getcwd()
    if root_dir is not None:
        os.chdir(root_dir)

    try:
        log("Creating %s with %s ...", archive, base_dir)
        zip = ZipFile(archive, mode, compression=ZIP_DEFLATED)
        for dirpath, dirnames, filenames in os.walk(base_dir):
            for name in filenames:
                path = os.path.normpath(os.path.join(dirpath, name))
                if os.path.isfile(path):
                    zip.write(path, path)
                    log("Adding %s" % path)
        zip.close()
    finally:
        if root_dir is not None:
            os.chdir(cwd)


def extract_zip(archive, outdir=os.curdir):
    """Extract a zip file.

    Extracts all the files to the 'outdir' directory."""
    zip = ZipFile(archive, "r")
    zip.extractall(outdir)
    zip.close()
