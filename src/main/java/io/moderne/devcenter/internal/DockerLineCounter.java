/*
 * Copyright 2026 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.moderne.devcenter.internal;

import org.jspecify.annotations.Nullable;
import org.openrewrite.docker.tree.Comment;
import org.openrewrite.docker.tree.Docker;
import org.openrewrite.docker.tree.Space;

import java.util.List;

/**
 * Counts lines in a Dockerfile LST by walking it in the order {@code DockerPrinter} emits text, without printing it.
 */
final class DockerLineCounter extends PrintOrderLineCounter {

    private DockerLineCounter() {
    }

    static long count(Docker.File file) {
        DockerLineCounter c = new DockerLineCounter();
        c.space(file.getPrefix());
        c.all(file.getGlobalArgs());
        c.all(file.getStages());
        c.space(file.getEof());
        return c.lineCount();
    }

    private void docker(@Nullable Docker docker) {
        if (docker == null) {
            return;
        }
        space(docker.getPrefix());
        if (docker instanceof Docker.Argument) {
            all(((Docker.Argument) docker).getContents());
        } else if (docker instanceof Docker.Literal) {
            Docker.Literal literal = (Docker.Literal) docker;
            if (literal.getQuoteStyle() != null) {
                syntax();
                text(literal.getText());
                syntax();
            } else {
                text(literal.getText());
            }
        } else if (docker instanceof Docker.EnvironmentVariable) {
            Docker.EnvironmentVariable variable = (Docker.EnvironmentVariable) docker;
            syntax();
            text(variable.getName());
            if (variable.isBraced()) {
                syntax();
            }
        } else if (docker instanceof Docker.Stage) {
            Docker.Stage stage = (Docker.Stage) docker;
            docker(stage.getFrom());
            all(stage.getInstructions());
        } else if (docker instanceof Docker.Run) {
            Docker.Run run = (Docker.Run) docker;
            text(run.getKeyword());
            flags(run.getFlags());
            docker(run.getCommand());
        } else if (docker instanceof Docker.Copy) {
            Docker.Copy copy = (Docker.Copy) docker;
            text(copy.getKeyword());
            flags(copy.getFlags());
            docker(copy.getForm());
        } else if (docker instanceof Docker.Add) {
            Docker.Add add = (Docker.Add) docker;
            text(add.getKeyword());
            flags(add.getFlags());
            docker(add.getForm());
        } else if (docker instanceof Docker.ShellForm) {
            docker(((Docker.ShellForm) docker).getArgument());
        } else if (docker instanceof Docker.ExecForm) {
            Docker.ExecForm exec = (Docker.ExecForm) docker;
            syntax();
            commaSeparated(exec.getArguments());
            space(exec.getClosingBracketPrefix());
            syntax();
        } else if (docker instanceof Docker.HeredocForm) {
            Docker.HeredocForm heredoc = (Docker.HeredocForm) docker;
            text(heredoc.getPreamble());
            docker(heredoc.getDestination());
            text("\n");
            all(heredoc.getBodies());
        } else if (docker instanceof Docker.HeredocBody) {
            Docker.HeredocBody body = (Docker.HeredocBody) docker;
            for (String line : body.getContentLines()) {
                text(line);
            }
            text(body.getClosing());
        } else if (docker instanceof Docker.CopyShellForm) {
            Docker.CopyShellForm form = (Docker.CopyShellForm) docker;
            all(form.getSources());
            docker(form.getDestination());
        } else if (docker instanceof Docker.Flag) {
            Docker.Flag flag = (Docker.Flag) docker;
            syntax();
            text(flag.getName());
            if (flag.getValue() != null) {
                syntax();
                docker(flag.getValue());
            }
        } else if (docker instanceof Docker.From) {
            Docker.From from = (Docker.From) docker;
            text(from.getKeyword());
            flags(from.getFlags());
            docker(from.getImageName());
            if (from.getTag() != null) {
                syntax();
                docker(from.getTag());
            }
            if (from.getDigest() != null) {
                syntax();
                docker(from.getDigest());
            }
            Docker.From.As as = from.getAs();
            if (as != null) {
                space(as.getPrefix());
                text(as.getKeyword());
                docker(as.getName());
            }
        } else if (docker instanceof Docker.Arg) {
            Docker.Arg arg = (Docker.Arg) docker;
            text(arg.getKeyword());
            docker(arg.getName());
            if (arg.getValue() != null) {
                syntax();
                docker(arg.getValue());
            }
        } else if (docker instanceof Docker.Env) {
            Docker.Env env = (Docker.Env) docker;
            text(env.getKeyword());
            for (Docker.Env.EnvPair pair : env.getPairs()) {
                pair(pair.getPrefix(), pair.getKey(), pair.isHasEquals(), pair.getValue());
            }
        } else if (docker instanceof Docker.Label) {
            Docker.Label label = (Docker.Label) docker;
            text(label.getKeyword());
            for (Docker.Label.LabelPair pair : label.getPairs()) {
                pair(pair.getPrefix(), pair.getKey(), pair.isHasEquals(), pair.getValue());
            }
        } else if (docker instanceof Docker.Cmd) {
            Docker.Cmd cmd = (Docker.Cmd) docker;
            text(cmd.getKeyword());
            docker(cmd.getCommand());
        } else if (docker instanceof Docker.Entrypoint) {
            Docker.Entrypoint entrypoint = (Docker.Entrypoint) docker;
            text(entrypoint.getKeyword());
            docker(entrypoint.getCommand());
        } else if (docker instanceof Docker.Expose) {
            Docker.Expose expose = (Docker.Expose) docker;
            text(expose.getKeyword());
            all(expose.getPorts());
        } else if (docker instanceof Docker.Port) {
            text(((Docker.Port) docker).getText());
        } else if (docker instanceof Docker.Volume) {
            Docker.Volume volume = (Docker.Volume) docker;
            text(volume.getKeyword());
            if (volume.isJsonForm()) {
                bracketed(volume.getOpeningBracketPrefix(), volume.getValues(), volume.getClosingBracketPrefix());
            } else {
                all(volume.getValues());
            }
        } else if (docker instanceof Docker.Shell) {
            Docker.Shell shell = (Docker.Shell) docker;
            text(shell.getKeyword());
            bracketed(shell.getOpeningBracketPrefix(), shell.getArguments(), shell.getClosingBracketPrefix());
        } else if (docker instanceof Docker.Workdir) {
            Docker.Workdir workdir = (Docker.Workdir) docker;
            text(workdir.getKeyword());
            docker(workdir.getPath());
        } else if (docker instanceof Docker.User) {
            Docker.User user = (Docker.User) docker;
            text(user.getKeyword());
            docker(user.getUser());
            if (user.getGroup() != null) {
                syntax();
                docker(user.getGroup());
            }
        } else if (docker instanceof Docker.Stopsignal) {
            Docker.Stopsignal stopsignal = (Docker.Stopsignal) docker;
            text(stopsignal.getKeyword());
            docker(stopsignal.getSignal());
        } else if (docker instanceof Docker.Onbuild) {
            Docker.Onbuild onbuild = (Docker.Onbuild) docker;
            text(onbuild.getKeyword());
            docker(onbuild.getInstruction());
        } else if (docker instanceof Docker.Healthcheck) {
            Docker.Healthcheck healthcheck = (Docker.Healthcheck) docker;
            text(healthcheck.getKeyword());
            if (healthcheck.isNone()) {
                space(healthcheck.getNonePrefix());
                syntax();
            } else {
                flags(healthcheck.getFlags());
                docker(healthcheck.getCmd());
            }
        } else if (docker instanceof Docker.Maintainer) {
            Docker.Maintainer maintainer = (Docker.Maintainer) docker;
            text(maintainer.getKeyword());
            docker(maintainer.getText());
        }
    }

    private void pair(Space prefix, Docker key, boolean hasEquals, Docker value) {
        space(prefix);
        docker(key);
        if (hasEquals) {
            syntax();
        }
        docker(value);
    }

    // "[" prefixed, comma-separated elements, then "]" prefixed
    private void bracketed(Space opening, List<? extends Docker> elements, Space closing) {
        space(opening);
        syntax();
        commaSeparated(elements);
        space(closing);
        syntax();
    }

    private void commaSeparated(List<? extends Docker> elements) {
        for (int i = 0; i < elements.size(); i++) {
            docker(elements.get(i));
            if (i < elements.size() - 1) {
                syntax();
            }
        }
    }

    private void flags(@Nullable List<Docker.Flag> flags) {
        if (flags != null) {
            all(flags);
        }
    }

    private void all(List<? extends Docker> nodes) {
        for (Docker node : nodes) {
            docker(node);
        }
    }

    // Comments are printed before the whitespace
    private void space(@Nullable Space space) {
        if (space == null) {
            return;
        }
        for (Comment comment : space.getComments()) {
            text(comment.getPrefix());
            text(comment.getText());
        }
        text(space.getWhitespace());
    }
}
