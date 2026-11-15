package com.oplus.ipemanager;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IUIPageCallback extends IInterface {
    String DESCRIPTOR = "com.oplus.ipemanager.IUIPageCallback";

    void clearIUIPageCbBinder(IBinder binder) throws RemoteException;
    void setSingleClickSensitivity(int sensitivity) throws RemoteException;
    void setWriteSoundEffectState(boolean state) throws RemoteException;
    void setFunctionFeedbackEnable(boolean enable) throws RemoteException;
    void setDemoModeEnable(boolean enable) throws RemoteException;
    void setWrittenFeedbackState(boolean state) throws RemoteException;
    void setWrittenFeedbackLevel(int level) throws RemoteException;
    boolean requestStatementAgree() throws RemoteException;
    String getConnectedPencilMac() throws RemoteException;
    int getUpdateStatus() throws RemoteException;
    int getConnectState() throws RemoteException;
    int getBatteryLevel() throws RemoteException;
    void setIPencilUIManagerCbBinder(IBinder binder) throws RemoteException;
    void setIUpdateOtaCbBinder(IBinder binder) throws RemoteException;
    void setIUIPageCbBinder(IBinder binder) throws RemoteException;

    abstract class Stub extends Binder implements IUIPageCallback {
        static final int TRANSACTION_clearIUIPageCbBinder = 1;
        static final int TRANSACTION_setSingleClickSensitivity = 2;
        static final int TRANSACTION_setWriteSoundEffectState = 3;
        static final int TRANSACTION_setFunctionFeedbackEnable = 4;
        static final int TRANSACTION_setDemoModeEnable = 5;
        static final int TRANSACTION_setWrittenFeedbackState = 6;
        static final int TRANSACTION_setWrittenFeedbackLevel = 7;
        static final int TRANSACTION_requestStatementAgree = 8;
        static final int TRANSACTION_getConnectedPencilMac = 9;
        static final int TRANSACTION_getUpdateStatus = 10;
        static final int TRANSACTION_getConnectState = 11;
        static final int TRANSACTION_getBatteryLevel = 12;
        static final int TRANSACTION_setIPencilUIManagerCbBinder = 13;
        static final int TRANSACTION_setIUpdateOtaCbBinder = 14;
        static final int TRANSACTION_setIUIPageCbBinder = 15;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IUIPageCallback asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IUIPageCallback) {
                return (IUIPageCallback) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        private static class Proxy implements IUIPageCallback {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public void clearIUIPageCbBinder(IBinder binder) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(binder);
                    mRemote.transact(TRANSACTION_clearIUIPageCbBinder, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setSingleClickSensitivity(int sensitivity) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(sensitivity);
                    mRemote.transact(TRANSACTION_setSingleClickSensitivity, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setWriteSoundEffectState(boolean state) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(state ? 1 : 0);
                    mRemote.transact(TRANSACTION_setWriteSoundEffectState, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setFunctionFeedbackEnable(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(enable ? 1 : 0);
                    mRemote.transact(TRANSACTION_setFunctionFeedbackEnable, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setDemoModeEnable(boolean enable) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(enable ? 1 : 0);
                    mRemote.transact(TRANSACTION_setDemoModeEnable, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setWrittenFeedbackState(boolean state) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(state ? 1 : 0);
                    mRemote.transact(TRANSACTION_setWrittenFeedbackState, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setWrittenFeedbackLevel(int level) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeInt(level);
                    mRemote.transact(TRANSACTION_setWrittenFeedbackLevel, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public boolean requestStatementAgree() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_requestStatementAgree, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt() != 0;
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public String getConnectedPencilMac() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getConnectedPencilMac, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readString();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int getUpdateStatus() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getUpdateStatus, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int getConnectState() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getConnectState, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public int getBatteryLevel() throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    mRemote.transact(TRANSACTION_getBatteryLevel, _data, _reply, 0);
                    _reply.readException();
                    return _reply.readInt();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setIPencilUIManagerCbBinder(IBinder binder) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(binder);
                    mRemote.transact(TRANSACTION_setIPencilUIManagerCbBinder, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setIUpdateOtaCbBinder(IBinder binder) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(binder);
                    mRemote.transact(TRANSACTION_setIUpdateOtaCbBinder, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }

            @Override
            public void setIUIPageCbBinder(IBinder binder) throws RemoteException {
                Parcel _data = Parcel.obtain();
                Parcel _reply = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder(binder);
                    mRemote.transact(TRANSACTION_setIUIPageCbBinder, _data, _reply, 0);
                    _reply.readException();
                } finally {
                    _reply.recycle();
                    _data.recycle();
                }
            }
        }
    }
}
