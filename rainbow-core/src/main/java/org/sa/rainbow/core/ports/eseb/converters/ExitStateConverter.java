package org.sa.rainbow.core.ports.eseb.converters;

import java.text.MessageFormat;

import org.sa.rainbow.core.Rainbow.ExitState;

import edu.cmu.cs.able.typelib.enumeration.EnumerationType;
import edu.cmu.cs.able.typelib.enumeration.EnumerationValue;
import edu.cmu.cs.able.typelib.jconv.TypelibJavaConversionRule;
import edu.cmu.cs.able.typelib.jconv.TypelibJavaConverter;
import edu.cmu.cs.able.typelib.jconv.ValueConversionException;
import edu.cmu.cs.able.typelib.prim.PrimitiveScope;
import edu.cmu.cs.able.typelib.scope.AmbiguousNameException;
import edu.cmu.cs.able.typelib.type.DataType;
import edu.cmu.cs.able.typelib.type.DataValue;
import incubator.pval.Ensure;

public class ExitStateConverter implements TypelibJavaConversionRule {

    private PrimitiveScope m_scope;

    public ExitStateConverter (PrimitiveScope scope) {
        m_scope = scope;
    }

    @Override
    public boolean handles_java (Object value, DataType dst) {
        Ensure.not_null (value);
        if (value instanceof ExitState) return dst == null || "exit_state".equals (dst.name ());
        return false;
    }

    @Override
    public boolean handles_typelib (DataValue value, Class<?> cls) {
        Ensure.not_null (value);
        if ("exit_state".equals (value.type ().name ())) return cls == null || ExitState.class.isAssignableFrom (cls);
        return false;
    }

    @Override
    public DataValue from_java (Object value, DataType dst, TypelibJavaConverter converter)
            throws ValueConversionException {
        if ((dst == null || dst instanceof EnumerationType) && value instanceof ExitState) {
            try {
                EnumerationType type = (EnumerationType )dst;
                if (type == null) {
                    type = (EnumerationType )m_scope.find ("exit_state");
                }
                ExitState exitState = (ExitState )value;
                switch (exitState) {
                case ABORT:
                    return type.value ("abort");
                case DESTRUCT:
                    return type.value ("destruct");
                case RESTART:
                    return type.value ("restart");
                case SLEEP:
                    return type.value ("sleep");
                default:
                    break;
                }
            }
            catch (AmbiguousNameException e) {
                throw new ValueConversionException (
                        MessageFormat.format ("Cannot convert from {0} to {1}", value.getClass ().getCanonicalName (),
                                (dst == null ? "exit_state" : dst.absolute_hname ().toString ())),
                        e);
            }

        }
        throw new ValueConversionException (
                MessageFormat.format ("Cannot convert from {0} to {1}", value.getClass ().getCanonicalName (),
                        (dst == null ? "exit_state" : dst.absolute_hname ().toString ())));
    }

    @Override
    public <T> T to_java (DataValue value, Class<T> cls, TypelibJavaConverter converter)
            throws ValueConversionException {
        if (value instanceof EnumerationValue) {
            EnumerationValue ev = (EnumerationValue )value;
            EnumerationType et = (EnumerationType )ev.type ();
            if (cls == null || cls == ExitState.class) {
                cls = (Class<T> )ExitState.class;
            }
            switch (ev.name ()) {
            case "abort":
                return (T )ExitState.ABORT;
            case "destrcut":
                return (T )ExitState.DESTRUCT;
            case "restart":
                return (T )ExitState.RESTART;
            case "sleep":
                return (T )ExitState.SLEEP;
            default:
                return (T )ExitState.SLEEP;
            }
        }
        throw new ValueConversionException (MessageFormat.format ("Could not convert from {0} to {1}",
                value.toString (), cls == null ? "ExitState" : cls.getCanonicalName ()));

    }

}
