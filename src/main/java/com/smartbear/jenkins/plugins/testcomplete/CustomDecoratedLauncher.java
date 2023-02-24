package com.smartbear.jenkins.plugins.testcomplete;

import hudson.Launcher;
import hudson.Proc;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.regex.Pattern;

public class CustomDecoratedLauncher extends Launcher.DecoratedLauncher {

    private final static String MASKED_PASSWORD = "********";
    private Pattern passwordsAsPattern;

    public CustomDecoratedLauncher(@Nonnull Launcher inner, Collection<String> passwords) {
        super(inner);

        passwordsAsPattern = null;

        if((passwords != null && passwords.size() > 0)) {
            StringBuilder regex = new StringBuilder().append('(');

            int nbMaskedPasswords = 0;

            for(String password: passwords) {
                if(StringUtils.isNotEmpty(password)) {
                    regex.append(Pattern.quote(password));
                    regex.append('|');
                    try {
                        String encodedPassword = URLEncoder.encode(password, "UTF-8");
                        if (!encodedPassword.equals(password)) {
                            regex.append(Pattern.quote(encodedPassword));
                            regex.append('|');
                        }
                    } catch (UnsupportedEncodingException e) {
                        // ignore
                    }
                    nbMaskedPasswords++;
                }
            }

            if (nbMaskedPasswords++ > 0) {
                regex.deleteCharAt(regex.length() - 1);
                regex.append(')');
                passwordsAsPattern = Pattern.compile(regex.toString());
            }
        }
    }

    @Override
    public Proc launch(ProcStarter ps) throws IOException {
        String[] cmdCopy = ps.cmds().toArray(new String[ps.cmds().size()]);

        if(passwordsAsPattern != null) {
            for (int i = 0; i < cmdCopy.length; i++) {
                cmdCopy[i] = passwordsAsPattern.matcher(cmdCopy[i]).replaceAll(MASKED_PASSWORD);
            }
        }

        printCommandLine(cmdCopy, ps.pwd());
        return getInner().launch(ps);
    }
}